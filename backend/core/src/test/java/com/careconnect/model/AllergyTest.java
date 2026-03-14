package com.careconnect.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AllergyTest {

    @Mock
    private Patient patient;

    // ─── No-arg constructor ───────────────────────────────────────────────────

    @Test
    void noArgConstructor_createsInstance() throws Exception {
        final Allergy allergy = new Allergy();

        assertThat(allergy).isNotNull();
        assertThat(allergy.getId()).isNull();
        assertThat(allergy.getAllergen()).isNull();
        assertThat(allergy.getAllergyType()).isNull();
        assertThat(allergy.getSeverity()).isNull();
        assertThat(allergy.getReaction()).isNull();
        assertThat(allergy.getNotes()).isNull();
        assertThat(allergy.getDiagnosedDate()).isNull();
    }

    // ─── All-args constructor ─────────────────────────────────────────────────

    @Test
    void allArgsConstructor_setsAllFields() throws Exception {
        final Instant now = Instant.now();
        final Allergy allergy = new Allergy(
                1L, patient, "Peanuts",
                Allergy.AllergyType.FOOD, Allergy.AllergySeverity.MILD,
                "Hives", "Avoid peanuts", "2020-01-01",
                true, now, now);

        assertThat(allergy.getId()).isEqualTo(1L);
        assertThat(allergy.getPatient()).isEqualTo(patient);
        assertThat(allergy.getAllergen()).isEqualTo("Peanuts");
        assertThat(allergy.getAllergyType()).isEqualTo(Allergy.AllergyType.FOOD);
        assertThat(allergy.getSeverity()).isEqualTo(Allergy.AllergySeverity.MILD);
        assertThat(allergy.getReaction()).isEqualTo("Hives");
        assertThat(allergy.getNotes()).isEqualTo("Avoid peanuts");
        assertThat(allergy.getDiagnosedDate()).isEqualTo("2020-01-01");
        assertThat(allergy.getIsActive()).isTrue();
        assertThat(allergy.getCreatedAt()).isEqualTo(now);
        assertThat(allergy.getUpdatedAt()).isEqualTo(now);
    }

    // ─── Builder defaults ─────────────────────────────────────────────────────

    @Test
    void builder_defaults_isActiveTrue() throws Exception {
        final Allergy allergy = Allergy.builder()
                .allergen("Peanuts")
                .allergyType(Allergy.AllergyType.FOOD)
                .build();

        assertThat(allergy.getIsActive()).isTrue();
    }

    // ─── Builder: all fields ──────────────────────────────────────────────────

    @Test
    void builder_allFields_setsCorrectly() throws Exception {
        final Instant now = Instant.now();

        final Allergy allergy = Allergy.builder()
                .id(1L)
                .allergen("Penicillin")
                .allergyType(Allergy.AllergyType.MEDICATION)
                .severity(Allergy.AllergySeverity.SEVERE)
                .reaction("Anaphylaxis")
                .notes("Avoid all penicillin-based drugs")
                .diagnosedDate("2020-05-01")
                .isActive(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertThat(allergy.getId()).isEqualTo(1L);
        assertThat(allergy.getAllergen()).isEqualTo("Penicillin");
        assertThat(allergy.getAllergyType()).isEqualTo(Allergy.AllergyType.MEDICATION);
        assertThat(allergy.getSeverity()).isEqualTo(Allergy.AllergySeverity.SEVERE);
        assertThat(allergy.getReaction()).isEqualTo("Anaphylaxis");
        assertThat(allergy.getNotes()).isEqualTo("Avoid all penicillin-based drugs");
        assertThat(allergy.getDiagnosedDate()).isEqualTo("2020-05-01");
        assertThat(allergy.getIsActive()).isTrue();
        assertThat(allergy.getCreatedAt()).isEqualTo(now);
        assertThat(allergy.getUpdatedAt()).isEqualTo(now);
    }

    // ─── Setters ──────────────────────────────────────────────────────────────

    @Test
    void lombokSetters_idPatientTimestamps() throws Exception {
        final Instant now = Instant.now();
        final Allergy allergy = new Allergy();

        allergy.setId(99L);
        allergy.setPatient(patient);
        allergy.setCreatedAt(now);
        allergy.setUpdatedAt(now);

        assertThat(allergy.getId()).isEqualTo(99L);
        assertThat(allergy.getPatient()).isEqualTo(patient);
        assertThat(allergy.getCreatedAt()).isEqualTo(now);
        assertThat(allergy.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void setters_updateFields() throws Exception {
        final Allergy allergy = new Allergy();

        allergy.setAllergen("Shellfish");
        allergy.setAllergyType(Allergy.AllergyType.FOOD);
        allergy.setSeverity(Allergy.AllergySeverity.MODERATE);
        allergy.setReaction("Hives");
        allergy.setNotes("Avoid all shellfish");
        allergy.setDiagnosedDate("2022-03-15");
        allergy.setIsActive(false);

        assertThat(allergy.getAllergen()).isEqualTo("Shellfish");
        assertThat(allergy.getAllergyType()).isEqualTo(Allergy.AllergyType.FOOD);
        assertThat(allergy.getSeverity()).isEqualTo(Allergy.AllergySeverity.MODERATE);
        assertThat(allergy.getReaction()).isEqualTo("Hives");
        assertThat(allergy.getNotes()).isEqualTo("Avoid all shellfish");
        assertThat(allergy.getDiagnosedDate()).isEqualTo("2022-03-15");
        assertThat(allergy.getIsActive()).isFalse();
    }

    // ─── toString() ───────────────────────────────────────────────────────────

    @Test
    void toString_containsAllergenField() throws Exception {
        final Allergy allergy = Allergy.builder().id(1L).allergen("Peanuts").allergyType(Allergy.AllergyType.FOOD).build();
        assertThat(allergy.toString()).isNotNull().contains("Peanuts");
    }

    // ─── @PrePersist: onCreate() ──────────────────────────────────────────────

    @Test
    void onCreate_setsCreatedAtAndUpdatedAt() throws Exception {
        final Allergy allergy = new Allergy();

        final Method m = Allergy.class.getDeclaredMethod("onCreate");
        m.setAccessible(true);
        m.invoke(allergy);

        assertThat(allergy.getCreatedAt()).isNotNull();
        assertThat(allergy.getUpdatedAt()).isNotNull();
    }

    @Test
    void onCreate_isActiveNull_setsToTrue() throws Exception {
        final Allergy allergy = new Allergy();
        allergy.setIsActive(null);   // reset the @Builder.Default true so we can test the null→true branch
        assertThat(allergy.getIsActive()).isNull();

        final Method m = Allergy.class.getDeclaredMethod("onCreate");
        m.setAccessible(true);
        m.invoke(allergy);

        assertThat(allergy.getIsActive()).isTrue();
    }

    @Test
    void onCreate_isActiveNotNull_doesNotOverride() throws Exception {
        final Allergy allergy = new Allergy();
        allergy.setIsActive(false);

        final Method m = Allergy.class.getDeclaredMethod("onCreate");
        m.setAccessible(true);
        m.invoke(allergy);

        assertThat(allergy.getIsActive()).isFalse();
    }

    // ─── @PreUpdate: onUpdate() ───────────────────────────────────────────────

    @Test
    void onUpdate_setsUpdatedAt() throws Exception {
        final Allergy allergy = new Allergy();

        final Method m = Allergy.class.getDeclaredMethod("onUpdate");
        m.setAccessible(true);
        m.invoke(allergy);

        assertThat(allergy.getUpdatedAt()).isNotNull();
    }

    // ─── AllergyType.fromJson() ───────────────────────────────────────────────

    @Test
    void allergyTypeFromJson_null_returnsOther() throws Exception {
        assertThat(Allergy.AllergyType.fromJson(null)).isEqualTo(Allergy.AllergyType.OTHER);
    }

    @Test
    void allergyTypeFromJson_drug_returnsMedication() throws Exception {
        assertThat(Allergy.AllergyType.fromJson("DRUG")).isEqualTo(Allergy.AllergyType.MEDICATION);
    }

    @Test
    void allergyTypeFromJson_medication_returnsMedication() throws Exception {
        assertThat(Allergy.AllergyType.fromJson("medication")).isEqualTo(Allergy.AllergyType.MEDICATION);
    }

    @Test
    void allergyTypeFromJson_food_returnsFood() throws Exception {
        assertThat(Allergy.AllergyType.fromJson("food")).isEqualTo(Allergy.AllergyType.FOOD);
    }

    @Test
    void allergyTypeFromJson_environmental_returnsEnvironmental() throws Exception {
        assertThat(Allergy.AllergyType.fromJson("ENVIRONMENTAL")).isEqualTo(Allergy.AllergyType.ENVIRONMENTAL);
    }

    @Test
    void allergyTypeFromJson_contact_returnsContact() throws Exception {
        assertThat(Allergy.AllergyType.fromJson("contact")).isEqualTo(Allergy.AllergyType.CONTACT);
    }

    @Test
    void allergyTypeFromJson_seasonal_returnsSeasonal() throws Exception {
        assertThat(Allergy.AllergyType.fromJson("seasonal")).isEqualTo(Allergy.AllergyType.SEASONAL);
    }

    @Test
    void allergyTypeFromJson_other_returnsOther() throws Exception {
        assertThat(Allergy.AllergyType.fromJson("other")).isEqualTo(Allergy.AllergyType.OTHER);
    }

    @Test
    void allergyTypeFromJson_unknown_returnsOther() throws Exception {
        assertThat(Allergy.AllergyType.fromJson("UNKNOWN_TYPE")).isEqualTo(Allergy.AllergyType.OTHER);
    }

    @Test
    void allergyTypeToJson_returnsName() throws Exception {
        assertThat(Allergy.AllergyType.FOOD.toJson()).isEqualTo("FOOD");
        assertThat(Allergy.AllergyType.MEDICATION.toJson()).isEqualTo("MEDICATION");
    }

    @Test
    void allergyTypeGetDisplayName_returnsDisplayName() throws Exception {
        assertThat(Allergy.AllergyType.FOOD.getDisplayName()).isEqualTo("Food Allergy");
        assertThat(Allergy.AllergyType.MEDICATION.getDisplayName()).isEqualTo("Medication Allergy");
        assertThat(Allergy.AllergyType.ENVIRONMENTAL.getDisplayName()).isEqualTo("Environmental Allergy");
        assertThat(Allergy.AllergyType.CONTACT.getDisplayName()).isEqualTo("Contact Allergy");
        assertThat(Allergy.AllergyType.SEASONAL.getDisplayName()).isEqualTo("Seasonal Allergy");
        assertThat(Allergy.AllergyType.OTHER.getDisplayName()).isEqualTo("Other");
    }

    // ─── AllergySeverity.fromJson() ───────────────────────────────────────────

    @Test
    void allergySeverityFromJson_null_returnsMild() throws Exception {
        assertThat(Allergy.AllergySeverity.fromJson(null)).isEqualTo(Allergy.AllergySeverity.MILD);
    }

    @Test
    void allergySeverityFromJson_mild_returnsMild() throws Exception {
        assertThat(Allergy.AllergySeverity.fromJson("mild")).isEqualTo(Allergy.AllergySeverity.MILD);
    }

    @Test
    void allergySeverityFromJson_moderate_returnsModerate() throws Exception {
        assertThat(Allergy.AllergySeverity.fromJson("MODERATE")).isEqualTo(Allergy.AllergySeverity.MODERATE);
    }

    @Test
    void allergySeverityFromJson_severe_returnsSevere() throws Exception {
        assertThat(Allergy.AllergySeverity.fromJson("severe")).isEqualTo(Allergy.AllergySeverity.SEVERE);
    }

    @Test
    void allergySeverityFromJson_lifeThreatening_returnsLifeThreatening() throws Exception {
        assertThat(Allergy.AllergySeverity.fromJson("LIFE_THREATENING"))
                .isEqualTo(Allergy.AllergySeverity.LIFE_THREATENING);
    }

    @Test
    void allergySeverityFromJson_unknown_returnsMild() throws Exception {
        assertThat(Allergy.AllergySeverity.fromJson("CRITICAL")).isEqualTo(Allergy.AllergySeverity.MILD);
    }

    @Test
    void allergySeverityToJson_returnsName() throws Exception {
        assertThat(Allergy.AllergySeverity.SEVERE.toJson()).isEqualTo("SEVERE");
        assertThat(Allergy.AllergySeverity.MILD.toJson()).isEqualTo("MILD");
    }

    @Test
    void allergySeverityGetDisplayName_returnsDisplayName() throws Exception {
        assertThat(Allergy.AllergySeverity.MILD.getDisplayName()).isEqualTo("Mild");
        assertThat(Allergy.AllergySeverity.MODERATE.getDisplayName()).isEqualTo("Moderate");
        assertThat(Allergy.AllergySeverity.SEVERE.getDisplayName()).isEqualTo("Severe");
        assertThat(Allergy.AllergySeverity.LIFE_THREATENING.getDisplayName()).isEqualTo("Life-threatening");
    }

    // ─── equals() and hashCode() (patient excluded) ───────────────────────────

    @Test
    void equals_sameFields_returnsTrue() throws Exception {
        final Allergy a1 = Allergy.builder().id(1L).allergen("Peanuts").allergyType(Allergy.AllergyType.FOOD).build();
        final Allergy a2 = Allergy.builder().id(1L).allergen("Peanuts").allergyType(Allergy.AllergyType.FOOD).build();

        assertThat(a1).isEqualTo(a2);
        assertThat(a1.hashCode()).isEqualTo(a2.hashCode());
    }

    @Test
    void equals_sameReference_returnsTrue() throws Exception {
        final Allergy a = Allergy.builder().id(1L).build();
        assertThat(a).isEqualTo(a);
    }

    @Test
    void equals_differentFields_returnsFalse() throws Exception {
        final Allergy a1 = Allergy.builder().id(1L).allergen("Peanuts").build();
        final Allergy a2 = Allergy.builder().id(2L).allergen("Shellfish").build();

        assertThat(a1).isNotEqualTo(a2);
    }

    @Test
    void equals_null_returnsFalse() throws Exception {
        final Allergy allergy = new Allergy();
        assertThat(allergy).isNotEqualTo(null);
    }

    @Test
    void equals_differentType_returnsFalse() throws Exception {
        final Allergy allergy = new Allergy();
        assertThat(allergy).isNotEqualTo("a string");
    }
}
