package com.careconnect.service;

import com.careconnect.exception.AppException;
import com.careconnect.model.Patient;
import com.careconnect.model.PatientRisk;
import com.careconnect.model.RiskType;
import com.careconnect.model.User;
import com.careconnect.repository.PatientRiskRepository;
import com.careconnect.repository.PatientRepository;
import com.careconnect.repository.RiskTypeRepository;
import com.careconnect.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class PatientRiskService {

  private final PatientRiskRepository patientRiskRepository;
  private final RiskTypeRepository riskTypeRepository;
  private final PatientRepository patientRepository;
  private final UserRepository userRepository;

  public PatientRiskService(PatientRiskRepository patientRiskRepository,
                              RiskTypeRepository riskTypeRepository,
                              PatientRepository patientRepository,
                              UserRepository userRepository) {
    this.patientRiskRepository = patientRiskRepository;
    this.riskTypeRepository = riskTypeRepository;
    this.patientRepository = patientRepository;
    this.userRepository = userRepository;
  }

  public List<RiskType> getAllRiskTypes() {
    return riskTypeRepository.findAllByOrderByNameAsc();
  }

  public List<PatientRisk> getFlaggedRisksForPatient(Long patientId) {
    return patientRiskRepository.findByPatientIdOrderByFlaggedAtDesc(patientId);
  }

  @Transactional
    public PatientRisk flagRisk(Long patientId, Long riskTypeId, Long flaggedByUserId) {
    Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Patient not found"));
    RiskType riskType = riskTypeRepository.findById(riskTypeId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Risk type not found"));
    User user = userRepository.findById(flaggedByUserId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));

    if (patientRiskRepository.existsByPatientIdAndRiskTypeId(patientId, riskTypeId)) {
      throw new AppException(HttpStatus.CONFLICT, "Risk already flagged for this patient");
    }

    PatientRisk risk = PatientRisk.builder()
                .patient(patient)
                .riskType(riskType)
                .flaggedBy(user)
                .flaggedAt(Instant.now())
                .build();
    return patientRiskRepository.save(risk);
  }

  @Transactional
    public void unflagRisk(Long patientId, Long riskId, Long currentUserId) {
    PatientRisk risk = patientRiskRepository.findById(riskId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Risk flag not found"));
    if (!risk.getPatient().getId().equals(patientId)) {
      throw new AppException(HttpStatus.NOT_FOUND, "Risk flag not found for this patient");
    }
    patientRiskRepository.delete(risk);
  }
}
