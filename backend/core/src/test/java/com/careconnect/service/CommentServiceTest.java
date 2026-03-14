package com.careconnect.service;

import com.careconnect.model.Comment;
import com.careconnect.repository.CommentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @InjectMocks
    private CommentService commentService;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("getCommentsForPost returns a list of comments for the given post")
    void getCommentsForPost_returnsList() throws Exception {
        final Long postId = 10L;
        final Comment comment1 = new Comment();
        comment1.setPostId(postId);
        comment1.setContent("First comment");
        final Comment comment2 = new Comment();
        comment2.setPostId(postId);
        comment2.setContent("Second comment");
        final List<Comment> expected = List.of(comment1, comment2);
        when(commentRepository.findByPostIdOrderByCreatedAtAsc(eq(postId))).thenReturn(expected);

        final List<Comment> result = commentService.getCommentsForPost(postId);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertSame(expected, result);
        verify(commentRepository).findByPostIdOrderByCreatedAtAsc(postId);
    }

    @Test
    @DisplayName("getCommentsForPost returns an empty list when no comments exist")
    void getCommentsForPost_returnsEmptyList() throws Exception {
        final Long postId = 99L;
        when(commentRepository.findByPostIdOrderByCreatedAtAsc(eq(postId)))
                .thenReturn(Collections.emptyList());

        final List<Comment> result = commentService.getCommentsForPost(postId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(commentRepository).findByPostIdOrderByCreatedAtAsc(postId);
    }

    @Test
    @DisplayName("addComment creates a Comment with correct fields and saves it")
    void addComment_savesAndReturnsComment() throws Exception {
        final Long postId = 5L;
        final Long userId = 42L;
        final String username = "testuser";
        final String content = "This is a test comment";

        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        final LocalDateTime beforeCall = LocalDateTime.now();
        final Comment result = commentService.addComment(postId, userId, username, content);
        final LocalDateTime afterCall = LocalDateTime.now();

        assertNotNull(result);
        assertEquals(postId, result.getPostId());
        assertEquals(userId, result.getUserId());
        assertEquals(username, result.getUsername());
        assertEquals(content, result.getContent());
        assertNotNull(result.getCreatedAt());
        assertFalse(result.getCreatedAt().isBefore(beforeCall));
        assertFalse(result.getCreatedAt().isAfter(afterCall));
        verify(commentRepository).save(any(Comment.class));
    }

    @Test
    @DisplayName("addComment returns the same object that repository.save returns")
    void addComment_returnsSavedComment() throws Exception {
        final Comment savedComment = new Comment();
        savedComment.setPostId(7L);
        savedComment.setContent("saved");
        when(commentRepository.save(any(Comment.class))).thenReturn(savedComment);

        final Comment result = commentService.addComment(7L, 3L, "user", "original");

        assertSame(savedComment, result);
    }
}
