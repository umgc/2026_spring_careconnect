package com.careconnect.repository;

import com.careconnect.model.CheckInQuestion;
import com.careconnect.model.CheckInQuestionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CheckInQuestionRepository extends JpaRepository<CheckInQuestion, CheckInQuestionId> {
  List<CheckInQuestion> findByCheckInIdOrderByOrdinalAsc(Long checkInId);
  boolean existsByCheckInIdAndQuestionId(Long checkInId, Long questionId);
}
