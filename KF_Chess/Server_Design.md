# Server Design - KF Chess (Distributed Architecture)

## 1. High-Level Architecture Overview

הארכיטקטורה מבוססת על מודל **Distributed Game Services** המפריד באופן מוחלט בין ניהול חיבורי ה-Network (Stateless/Edge Services) לבין הרצת לוגיקת המשחק (Stateful Game Engine Shards) והודעות פנימיות עתירות עומס.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                CLIENT APPLICATIONS                              │
└──────────────────────┬──────────────────────────────────┬───────────────────────┘
                       │ REST / HTTP                      │ WebSocket
                       ▼                                  ▼
             ┌──────────────────┐               ┌──────────────────┐
             │   API Gateway    │               │   WS Gateways    │
             │  (Auth, Rooms)   │               │   (Async I/O)    │
             └─────────┬────────┘               └─────────┬────────┘
                       │                                  │
───────────────────────┼──────────────────────────────────┼────────────────────────
                       │   NATS EVENT BUS (Internal Bus)  │
                       ▼                                  ▼
            ┌─────────────────────┐            ┌────────────────────┐
            │ Matchmaker Service  │───────────►│   Game Allocator   │
            └─────────────────────┘            └──────────┬─────────┘
                                                          │
                                                          ▼
                                              ┌──────────────────────┐
                                              │ Game Server Shards   │
                                              │ (Authoritative Engine│
                                              └──────────┬─────────┘
                                                         │
               ┌─────────────────────────────────────────┼─────────────────────────────────────────┐
               ▼                                         ▼                                         ▼
     ┌───────────────────┐                     ┌───────────────────┐                     ┌───────────────────┐
     │   PostgreSQL DB   │                     │    Redis Cache    │                     │   Observability   │
     │ (Users, ELO, Logs)│                     │(Sessions, Queues) │                     │ (Metrics, Traces) │
     └───────────────────┘                     └───────────────────┘                     └───────────────────┘
```

---

## 2. Infrastructure & Container Orchestration (Docker, Kubernetes, K3s)

* **Docker:** מארז (Package) מבודד לכל מיקרו-סרוויס.
* **Kubernetes (K8s) vs K3s:**
  * **K3s:** ישמש **אך ורק לסביבת פיתוח מקומית (Local Dev/Testing)** ול-Docker Compose מורחב. הוא אינו מתאים לייצור בקנה מידה של 10 מיליון משתמשים פעילים בו-זמנית בשל מגבלות ב-Control Plane.
  * **K8s (Full Cluster):** ישמש בסביבת ה-Production. מספק HA מלא ל-etcd, CNI מתקדם (eBPF/Cilium) לתעבורת הרשת העצומה, ותמיכה ב-Horizontal Pod Autoscaler (HPA).
* **Agones Integration (Optional):** ניהול ו-Scaling ייעודי ל-Game Server Shards מעל Kubernetes.

---

## 3. Core Component Responsibilities

1. **API Gateway (HTTP/REST):**
   * מטפל בפעולות שלא ברצף זמן אמת: `Login`, `Register`, `Fetch Profile`, `Game History`.
   * מעביר בקשות אימות מול ה-`Auth Service` ומול PostgreSQL.

2. **WebSocket (WS) Gateways:**
   * מנהל את חיבורי ה-WebSocket החיים מול ה-Clients בלבד (Async I/O non-blocking).
   * **ללא תליית Thread לכל לקוח (No thread-per-client)**.
   * לא מריץ לוגיקת שחמט! תפקידו בלבד: תרגום פרוטוקול Client $\leftrightarrow$ NATS Event Bus.

3. **Matchmaker & Game Allocator:**
   * **Matchmaker:** מנהל את תור המתמודדים בעזרת Redis (ZSET לפי ELO) ומצוות זוגות שחקנים.
   * **Game Allocator:** מקבל את שידוך השחקנים, איתור Game Server Shards פנוי, ומקצה את ה-`roomId` לשרת הספציפי.

4. **Game Server Shards (The Core):**
   * מחזיק את ה-**`GameEngine` הראשי (Single Source of Truth)**. ה-Client וה-Gateways אינם מחליטים על חוקי המשחק.
   * מריץ עשרות אלפי חדרים בזיכרון ה-Java במקביל.
   * **תיקון קריטי ל-`GameLoopRunner`:** ביטול השידור האוטומטי כל 30ms! שידור עדכוני state יבוצע **אך ורק בעת שינוי מצב הלוח (Event-Driven / Delta Updates)** כדי למנוע קריסת רשת.

5. **NATS Event Bus:**
   * ערוץ התקשורת הפנימי המהיר ביותר בין ה-WS Gateways לבין ה-Game Server Shards.
   * מאפשר הפרדה מלאה - אם שרת משחק קורס, חיבור ה-WebSocket של המשתמש מול ה-Gateway נשאר חי.

---

## 4. Data Layer Architecture (PostgreSQL & Redis)

* **SQLite Limitation:** לא מתאים למערכת מבוזרת מרובת שרתים בגלל Single-writer lock ומגבלת קובץ מקומי.
* **PostgreSQL (Sharded / Cluster):**
  * שומר מידע קבוע: משתמשים (`Users`), סיסמאות, דירוגי ELO, והיסטוריית משחקים.
  * **התאמה לקוד:** ה-`DatabaseManager` הופך **לאסינכרוני לחלוטין (Non-blocking I/O)**. קריאות כגון `updateUserRating` ב-`GameRoom.endGame()` מבוצעות ברקע מבלי לחסום את שרת המשחק.
* **Redis Cluster:**
  * **Sessions & Active Rooms:** מיפוי משתמש לשרת ה-WS שלו, ומיפוי `roomId` ל-Game Shard.
  * **Matchmaking Queue:** שימוש ב-Redis Sorted Sets (ZSET) לניהול תור מבוזר.
  * **Reconnect Buffer:** שמירת מצב זמני לאפשרות התחברות מחדש.

---

## 5. Game Duration (30–90s) & Graceful Shutdown

* **No Pod-per-Game:** בגלל תחלופת המשחקים המהירה (30–90 שניות), **אין ליצור Docker/Pod חדש לכל משחק**. התקורה של הרמת Pod (2–10 שניות) תביא להקרסת ה-Kubernetes Control Plane.
* **Warm Pool Architecture:** שרת מקצה (Shard) פועל כתוכנה קבועה שמריצה ומפנה אלפי חדרים בזיכרון ה-RAM במקביל.
* **Graceful Shutdown Process:**
  1. בעת הקטנת עומס (Scale In), ה-Game Allocator מסמן את ה-Shard במצב `DRAINING` (לא מקבל משחקים חדשים).
  2. השרת ממתין מקסימום 90 שניות עד לסיום כל המשחקים הפעילים.
  3. ה-Pod נסגר בצורה נקיות ללא קטיעת משחקים באמצע.

---

## 6. Implementation Strategy & Next Steps

1. **Docker Compose (גרסה קטנה עובדת):**
   * יצירת `docker-compose.yml` המרים: `API Gateway`, `WS Gateway`, `Game Server`, `Redis`, `PostgreSQL`, ו-`NATS`.
2. **התאמת קוד ה-Java הקיים:**
   * הפיכת `DatabaseManager` לאסינכרוני.
   * שינוי מנגנון השידור ב-`GameLoopRunner` ל-Event-based בלבד.
   * הוצאת ה-Maps המקומיים ב-`RoomRegistry` והחלפתם ב-Redis Client / NATS Events.
