package textIO;

import org.example.controllers.Controller;
import org.example.textIO.ScriptRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScriptRunnerTest {

    @Mock
    private Controller mockController;

    @InjectMocks
    private ScriptRunner scriptRunner;

    @Test
    @DisplayName("ScriptRunner lifecycle should transition from reading board to correctly delegating controller commands")
    void handleInputLine_FullScriptFlow_DelegatesToController() {
        // 1. Arrange - נזין את הלוח בתהליך זורם
        scriptRunner.handleInputLine("Board:");
        scriptRunner.handleInputLine("wK .");
        scriptRunner.handleInputLine(".  bP");

        // 2. מעבר למצב פקודות - בנקודה זו ה-ScriptRunner מאתחל פנימית את ה-Controller האמיתי
        scriptRunner.handleInputLine("Commands:");

        // לצורך בדיקת יחידה מבודדת, נשתמש ב-Reflection כדי להחליף את ה-Controller האמיתי שנוצר ב-Mock שלנו
        ReflectionTestUtils.setField(scriptRunner, "controller", mockController);

        // 3. Act - ביצוע הפקודות מהסקריפט
        scriptRunner.handleInputLine("click 100 150");
        scriptRunner.handleInputLine("wait 500");
        scriptRunner.handleInputLine("jump 0 50");

        // 4. Assert - וידוא שכל הפקודות תורגמו ונשלחו לקונטרולר בפרמטרים הנכונים
        verify(mockController).click(100, 150);
        verify(mockController).wait_(500L);
        verify(mockController).jump(0, 50);
    }
}
