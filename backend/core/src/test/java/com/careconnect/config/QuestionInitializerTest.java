package com.careconnect.config;

import com.careconnect.model.Question;
import com.careconnect.model.QuestionType;
import com.careconnect.repository.QuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuestionInitializerTest {

    @Mock
    private QuestionRepository questionRepository;

    @InjectMocks
    private QuestionInitializer questionInitializer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void initQuestions_DoesNothingIfQuestionsExist() {
        when(questionRepository.count()).thenReturn(5L);

        questionInitializer.initQuestions();

        verify(questionRepository, never()).save(any(Question.class));
        verify(questionRepository, times(1)).count();
    }

    @Test
    void initQuestions_CreatesAllQuestionsWhenEmpty() {
        when(questionRepository.count()).thenReturn(0L);
        when(questionRepository.save(any(Question.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        questionInitializer.initQuestions();

        verify(questionRepository, times(15)).save(any(Question.class));
        verify(questionRepository, atLeast(2)).count(); // before and after
    }

    @Test
    void initQuestions_SavesCorrectFirstQuestion() {
        when(questionRepository.count()).thenReturn(0L);

        ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
        when(questionRepository.save(captor.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        questionInitializer.initQuestions();

        List<Question> saved = captor.getAllValues();

        Question first = saved.get(0);

        assertEquals("Did you take all of your prescribed medications today?", first.getPrompt());
        assertEquals(QuestionType.YES_NO, first.getType());
        assertTrue(first.isRequired());
        assertTrue(first.isActive());
        assertEquals(1, first.getOrdinal());
    }

    @Test
    void initQuestions_AllOrdinalsAreSequential() {
        when(questionRepository.count()).thenReturn(0L);

        ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
        when(questionRepository.save(captor.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        questionInitializer.initQuestions();

        List<Question> saved = captor.getAllValues();

        assertEquals(15, saved.size());

        for (int i = 0; i < saved.size(); i++) {
            assertEquals(i + 1, saved.get(i).getOrdinal());
        }
    }

    @Test
    void initQuestions_ContinuesIfSaveThrows() {
        when(questionRepository.count()).thenReturn(0L);

        when(questionRepository.save(any(Question.class)))
                .thenThrow(new RuntimeException("DB failure"));

        assertDoesNotThrow(() -> questionInitializer.initQuestions());

        verify(questionRepository, times(15)).save(any(Question.class));
    }

    @Test
    void initQuestions_HandlesCountExceptionGracefully() {
        when(questionRepository.count())
                .thenThrow(new RuntimeException("Database unavailable"));

        assertDoesNotThrow(() -> questionInitializer.initQuestions());

        verify(questionRepository, times(1)).count();
        verify(questionRepository, never()).save(any());
    }

    @Test
    void initQuestions_CreatesAllExpectedQuestionTypes() {
        when(questionRepository.count()).thenReturn(0L);

        ArgumentCaptor<Question> captor = ArgumentCaptor.forClass(Question.class);
        when(questionRepository.save(captor.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        questionInitializer.initQuestions();

        List<Question> saved = captor.getAllValues();

        long yesNoCount = saved.stream()
                .filter(q -> q.getType() == QuestionType.YES_NO)
                .count();

        long trueFalseCount = saved.stream()
                .filter(q -> q.getType() == QuestionType.TRUE_FALSE)
                .count();

        long numberCount = saved.stream()
                .filter(q -> q.getType() == QuestionType.NUMBER)
                .count();

        long textCount = saved.stream()
                .filter(q -> q.getType() == QuestionType.TEXT)
                .count();

        assertTrue(yesNoCount > 0);
        assertTrue(trueFalseCount > 0);
        assertTrue(numberCount > 0);
        assertTrue(textCount > 0);
    }
}
