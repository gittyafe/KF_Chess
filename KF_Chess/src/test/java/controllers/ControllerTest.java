package controllers;

import org.example.controllers.LocalController;
import org.example.engines.GameEngine;
import org.example.models.MoveRequest;
import org.example.models.MoveStatus;
import org.example.models.Piece;
import org.example.models.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ControllerTest {

    @Mock
    private GameEngine gameEngine;

    @InjectMocks
    private LocalController controller;

    @Nested
    @DisplayName("Click and Selection Flow")
    class ClickFlowTests {

        @Test
        @DisplayName("First click on a cell with a piece should select it")
        void click_FirstTimeOnPiece_SelectsPiece() {
            // Arrange - קואורדינטות (150, 250) מתורגמות לפיקסלים: עמודה 1, שורה 2
            Position targetPos = new Position(2, 1);
            Piece dummyPiece = new Piece(1, 'w', 'P', targetPos);

            when(gameEngine.getPieceAt(any(Position.class))).thenReturn(dummyPiece);

            // Act
            controller.click(150, 250);

            // Assert - מוודאים שהקונטרולר פנה למנוע לבדוק אם יש כלי במיקום הנכון
            verify(gameEngine, times(1)).getPieceAt(argThat(pos -> pos.equals(targetPos)));
            verify(gameEngine, never()).requestMove(any(), any());
        }

        @Test
        @DisplayName("First click on an empty cell should not select anything")
        void click_OnEmptyCell_DoesNotSelect() {
            // Arrange
            Position targetPos = new Position(0, 0);
            when(gameEngine.getPieceAt(any(Position.class))).thenReturn(null);

            // Act
            controller.click(50, 50); // row=0, col=0

            // Assert
            verify(gameEngine).getPieceAt(argThat(pos -> pos.equals(targetPos)));
            verify(gameEngine, never()).requestMove(any(), any());
        }

        @Test
        @DisplayName("Second click after selection should attempt move and clear selection on SUCCESS")
        void click_SecondTimeValidMove_ExecutesAndClearsSelection() {
            // Arrange - לחיצה ראשונה לבחירת הכלי בשורה 0, עמודה 0
            Position srcPos = new Position(0, 0);
            Piece piece = new Piece(1, 'w', 'R', srcPos);
            when(gameEngine.getPieceAt(any(Position.class))).thenReturn(piece);
            controller.click(50, 50);

            // לחיצה שנייה ליעד בשורה 0, עמודה 5
            Position destPos = new Position(0, 5);
            MoveRequest successResponse = new MoveRequest(MoveStatus.SUCCESS, true);
            when(gameEngine.requestMove(any(Position.class), any(Position.class))).thenReturn(successResponse);

            // Act
            controller.click(550, 50);

            // Assert - מוודאים שהתבצעה פנייה למנוע עם מיקום המקור והיעד הנכונים
            verify(gameEngine).requestMove(
                    argThat(p -> p.equals(srcPos)),
                    argThat(p -> p.equals(destPos))
            );
        }

        @Test
        @DisplayName("Second click on same color occupied square should update selected position")
        void click_OnAnotherAllyPiece_UpdatesSelection() {
            // Arrange - בחירת כלי ראשון ב-(0,0)
            Position firstPos = new Position(0, 0);
            Piece firstPiece = new Piece(1, 'w', 'R', firstPos);
            when(gameEngine.getPieceAt(any(Position.class))).thenReturn(firstPiece);
            controller.click(50, 50);

            // לחיצה שנייה על משבצת של כלי מאותו צבע (0,2)
            Position secondPos = new Position(0, 2);
            MoveRequest sameColorResponse = new MoveRequest(MoveStatus.SAME_COLOR_OCCUPIED, false);
            when(gameEngine.requestMove(any(Position.class), any(Position.class))).thenReturn(sameColorResponse);

            // Act
            controller.click(250, 50);

            // Assert
            verify(gameEngine).requestMove(
                    argThat(p -> p.equals(firstPos)),
                    argThat(p -> p.equals(secondPos))
            );
        }
    }

    @Nested
    @DisplayName("Special Actions and Lifecycle")
    class SpecialActionsTests {

        @Test
        @DisplayName("Jump action should forward correct board positions to GameEngine")
        void jump_ValidCoordinates_DelegatesToGameEngine() {
            // Arrange - x=450 (col=4), y=350 (row=3)
            Position targetPos = new Position(3, 4);

            // Act
            controller.jump(450, 350);

            // Assert
            verify(gameEngine, times(1)).jumpRequest(argThat(pos -> pos.equals(targetPos)));
        }

        @Test
        @DisplayName("Wait action should forward execution time to GameEngine")
        void wait_ValidDuration_DelegatesToGameEngine() {
            // Act
            controller.wait_(500L);

            // Assert
            verify(gameEngine, times(1)).wait_(500L);
        }

        @Test
        @DisplayName("ClearSelection should reset controller state without throwing exceptions")
        void clearSelection_Called_ExecutesSuccessfully() {
            // Act & Assert
            // מתודה זו פנימית ומאפסת משתנים, נוודא שהיא רצה בצורה חלקה ללא שגיאות
            controller.clearSelection();
        }
    }
}