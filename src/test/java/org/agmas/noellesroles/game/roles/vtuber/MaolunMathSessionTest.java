package org.agmas.noellesroles.game.roles.vtuber;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.agmas.noellesroles.game.roles.vtuber.MaolunMathSession.Result.*;

class MaolunMathSessionTest {
    private MaolunMathSession.Result correct(MaolunMathSession session) {
        return session.answer(session.id(), session.sequence(), session.question().getCorrectIndex(), 10);
    }

    private MaolunMathSession.Result wrong(MaolunMathSession session) {
        return session.answer(session.id(), session.sequence(), (session.question().getCorrectIndex() + 1) % 4, 10);
    }

    @Test void requiresFiveConsecutiveServerValidatedAnswers() {
        var session = new MaolunMathSession(1200);
        for (int i = 0; i < 4; i++) assertEquals(NEXT_QUESTION, correct(session));
        assertEquals(SUCCESS, correct(session));
        assertEquals(IGNORED, correct(session));
    }

    @Test void aMistakeResetsTheStreakAndTheSecondMistakeFails() {
        var session = new MaolunMathSession(1200);
        for (int i = 0; i < 4; i++) correct(session);
        assertEquals(NEXT_QUESTION, wrong(session));
        assertEquals(0, session.consecutiveCorrect());
        assertEquals(1, session.remainingMistakes());
        correct(session);
        assertEquals(FAILURE, wrong(session));
        assertEquals(IGNORED, correct(session));
    }

    @Test void aPlayerCanRecoverFromOneMistakeWithFiveNewCorrectAnswers() {
        var session = new MaolunMathSession(1200);
        wrong(session);
        for (int i = 0; i < 4; i++) assertEquals(NEXT_QUESTION, correct(session));
        assertEquals(SUCCESS, correct(session));
    }

    @Test void rejectsOtherSessionsRepeatedAnswersAndInvalidChoices() {
        var session = new MaolunMathSession(1200);
        int choice = session.question().getCorrectIndex();
        assertEquals(IGNORED, session.answer(UUID.randomUUID(), 0, choice, 10));
        assertEquals(IGNORED, session.answer(session.id(), 0, 4, 10));
        assertEquals(NEXT_QUESTION, session.answer(session.id(), 0, choice, 10));
        assertEquals(IGNORED, session.answer(session.id(), 0, choice, 10));
        assertEquals(1, session.consecutiveCorrect());
    }

    @Test void deadlineCannotBeBypassedByReportingTheRightAnswer() {
        var session = new MaolunMathSession(1200);
        assertEquals(FAILURE, session.answer(session.id(), 0, session.question().getCorrectIndex(), 1200));
        assertEquals(IGNORED, correct(session));
    }

    @Test void closingTheScreenCountsAsFailureEvenWhileTheNextQuestionIsInFlight() {
        var session = new MaolunMathSession(1200);
        correct(session);
        assertEquals(FAILURE, session.answer(session.id(), 0, -1, 10));
        assertEquals(IGNORED, correct(session));
    }
}
