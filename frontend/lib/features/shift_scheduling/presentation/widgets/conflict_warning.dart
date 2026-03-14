import 'package:flutter/material.dart';
import 'package:care_connect_app/features/shift_scheduling/models/scheduled_visit_model.dart';

class ConflictWarning extends StatelessWidget {
  final VisitConflict conflict;
  final VoidCallback? onDismiss;

  const ConflictWarning({super.key, required this.conflict, this.onDismiss});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.all(8),
      decoration: BoxDecoration(
        color: Colors.red.shade50,
        border: Border.all(color: Colors.red, width: 2),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(12.0),
            child: Row(
              children: [
                Icon(Icons.warning, color: Colors.red.shade700, size: 28),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Schedule Conflict Detected',
                        style: TextStyle(
                          fontWeight: FontWeight.bold,
                          color: Colors.red.shade700,
                          fontSize: 14,
                        ),
                      ),
                      Text(
                        conflict.message,
                        style: TextStyle(
                          color: Colors.red.shade600,
                          fontSize: 12,
                        ),
                      ),
                    ],
                  ),
                ),
                if (onDismiss != null)
                  IconButton(
                    icon: const Icon(Icons.close),
                    color: Colors.red.shade700,
                    onPressed: onDismiss,
                  ),
              ],
            ),
          ),
          if (conflict.conflictingVisits.isNotEmpty)
            Container(
              decoration: BoxDecoration(
                color: Colors.red.shade100,
                borderRadius: const BorderRadius.only(
                  bottomLeft: Radius.circular(8),
                  bottomRight: Radius.circular(8),
                ),
              ),
              child: ListView.builder(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: conflict.conflictingVisits.length,
                itemBuilder: (context, index) {
                  final visit = conflict.conflictingVisits[index];
                  return Padding(
                    padding: const EdgeInsets.all(12.0),
                    child: Row(
                      children: [
                        const Icon(Icons.event, size: 16, color: Colors.red),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                visit.patientName,
                                style: const TextStyle(
                                  fontWeight: FontWeight.bold,
                                  fontSize: 12,
                                ),
                              ),
                              Text(
                                '${visit.scheduledTime.format(context)} - ${visit.getEndTime()}',
                                style: const TextStyle(
                                  fontSize: 11,
                                  color: Colors.grey,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  );
                },
              ),
            ),
        ],
      ),
    );
  }
}
