package models;

public class Position {
    private int row;
    private int column;

    public Position(int row, int column) {
        this.row = row;
        this.column = column;
    }

    public int getRow() {
        return row;
    }

    public int getColumn() {
        return column;
    }
    
    public boolean equals(Position other){
        return this.row == other.getRow() && this.column == other.getColumn();
    }
}
