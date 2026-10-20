package org.agmas.noellesroles.game.roles.vtuber;

import java.util.UUID;
import org.agmas.noellesroles.utils.MathProblemsManager;
import org.agmas.noellesroles.utils.MathProblemsManager.MathProblem;

/** Server-owned questions; clients submit a choice, never a success flag. */
public final class MaolunMathSession {
    public enum Result { IGNORED, NEXT_QUESTION, SUCCESS, FAILURE }

    private final UUID id = UUID.randomUUID();
    private final long deadline;
    private final MathProblemsManager problems = new MathProblemsManager();
    private MathProblem question = problems.generateProblem(1);
    private int sequence;
    private int consecutiveCorrect;
    private int mistakes;
    private boolean finished;

    public MaolunMathSession(long deadline) { this.deadline = deadline; }

    public Result answer(UUID sessionId, int questionSequence, int choice, long now) {
        if (finished || !id.equals(sessionId)) return Result.IGNORED;
        if (choice == -1 || now >= deadline) {
            finished = true;
            return Result.FAILURE;
        }
        if (questionSequence != sequence || choice < 0 || choice >= question.getOptions().size()) {
            return Result.IGNORED;
        }
        if (choice == question.getCorrectIndex()) {
            consecutiveCorrect++;
            if (consecutiveCorrect == 5) {
                finished = true;
                return Result.SUCCESS;
            }
        } else {
            consecutiveCorrect = 0;
            if (++mistakes == 2) {
                finished = true;
                return Result.FAILURE;
            }
        }
        sequence++;
        question = problems.generateProblem(1);
        return Result.NEXT_QUESTION;
    }

    public UUID id() { return id; }
    public long deadline() { return deadline; }
    public int sequence() { return sequence; }
    public int consecutiveCorrect() { return consecutiveCorrect; }
    public int remainingMistakes() { return 2 - mistakes; }
    public MathProblem question() { return question; }
}
