package models;

public class Piece {
    private int id;
    private char color;
    private char type;
    private Position square;
    private STATE state;

    // הקונסטרקטורים מנקים את שדות ה-Rule לחלוטין
    public Piece(int id, char color, char type, Position square) {
        this(id, color, type, square, STATE.IDLE);
    }

    public Piece(int id, char color, char type, Position square, STATE state) {
        this.id = id;
        this.color = color;
        this.type = type;
        this.square = square;
        this.state = state;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public char getColor() { return color; }
    public void setColor(char color) { this.color = color; }

    public char getType() { return type; }
    public void setType(char type) { this.type = type; }

    public Position getSquare() { return square; }
    public void setSquare(Position square) { this.square = square; }

    public STATE getState() { return state; }
    public void setState(STATE state) { this.state = state; }

    /**
     * כעת מתודת promote היא פשוטה ואטומית לחלוטין! ללא Switch וללא New.
     */
    public void promote(char newType) {
        this.type = newType;
    }
}


// package models;

// import rules.IPieceRule;

// /**
//  * Represents a chess piece on the board.
//  * Composite object: combines color + type + position + state.
//  */
// public class Piece {
//     private int id;
//     private char color;
//     private char type;
//     private Position square;
//     private STATE state;
//     private IPieceRule rule;

//     public Piece(int id, char color, char type, Position square, IPieceRule rule) {
//         this(id, color, type, square, STATE.IDLE, rule);
//     }

//     public Piece(int id, char color, char type, Position square, STATE state, IPieceRule rule) {
//         this.id = id;
//         this.color = color;
//         this.type = type;
//         this.square = square;
//         this.state = state;
//         this.rule = rule;
//     }

//     public IPieceRule getPieceRule() {
//         return rule;
//     }

//     public int getId() {
//         return id;
//     }

//     public void setId(int id) {
//         this.id = id;
//     }

//     public char getColor() {
//         return color;
//     }

//     public void setColor(char color) {
//         this.color = color;
//     }

//     public char getType() {
//         return type;
//     }

//     public void setType(char type) {
//         this.type = type;
//     }

//     public Position getSquare() {
//         return square;
//     }

//     public void setSquare(Position square) {
//         this.square = square;
//     }

//     public STATE getState() {
//         return state;
//     }

//     public void setState(STATE state) {
//         this.state = state;
//     }

//     public void promote(char newType) {
//         this.type = newType;
//         switch (Character.toUpperCase(newType)) {
//             case 'R':
//                 this.rule = new rules.RookRule();
//                 break;
//             case 'K':
//                 this.rule = new rules.KingRule();
//                 break;
//             case 'P':
//                 this.rule = new rules.PawnRule();
//                 break;
//             case 'B':
//                 this.rule = new rules.BishopRule();
//                 break;
//             case 'Q':
//                 this.rule = new rules.QueenRule();
//                 break;
//             case 'N':
//                 this.rule = new rules.KnightRule();
//                 break;
//             default:
//                 throw new IllegalArgumentException("Unknown piece type: " + newType);
//         }
//     }

// }