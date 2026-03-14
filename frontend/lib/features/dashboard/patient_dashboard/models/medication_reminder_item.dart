class MedicationReminderItem {
  final int medicationId;
  final String medicationName;
  final String dosage;
  final String frequency;
  final DateTime nextDueAt;
  final bool isTakenForCurrentWindow;

  const MedicationReminderItem({
    required this.medicationId,
    required this.medicationName,
    required this.dosage,
    required this.frequency,
    required this.nextDueAt,
    required this.isTakenForCurrentWindow,
  });
}
