import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:care_connect_app/features/shift_scheduling/models/scheduled_visit_model.dart';
import 'package:care_connect_app/features/shift_scheduling/services/schedule_api_service.dart';

class AuditHistorySheet extends StatefulWidget {
  final int visitId;

  const AuditHistorySheet({
    super.key,
    required this.visitId,
  });

  @override
  State<AuditHistorySheet> createState() => _AuditHistorySheetState();
}

class _AuditHistorySheetState extends State<AuditHistorySheet> {
  late Future<List<ScheduledVisitAudit>> _auditFuture;
  late ScheduleApiService _apiService;

  @override
  void initState() {
    super.initState();
    _apiService = ScheduleApiService();
    _auditFuture = _apiService.getAuditHistory(widget.visitId);
  }

  Color _getActionColor(String action) {
    switch (action.toUpperCase()) {
      case 'CREATED':
        return Colors.green;
      case 'UPDATED':
        return Colors.blue;
      case 'DELETED':
        return Colors.red;
      default:
        return Colors.grey;
    }
  }

  IconData _getActionIcon(String action) {
    switch (action.toUpperCase()) {
      case 'CREATED':
        return Icons.add_circle;
      case 'UPDATED':
        return Icons.edit;
      case 'DELETED':
        return Icons.delete;
      default:
        return Icons.info;
    }
  }

  @override
  Widget build(BuildContext context) {
    return DraggableScrollableSheet(
      expand: false,
      builder: (context, scrollController) {
        return Container(
          decoration: const BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.only(
              topLeft: Radius.circular(16),
              topRight: Radius.circular(16),
            ),
          ),
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.all(16.0),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      'Schedule Change History',
                      style: Theme.of(context).textTheme.headlineSmall,
                    ),
                    IconButton(
                      icon: const Icon(Icons.close),
                      onPressed: () => Navigator.pop(context),
                    ),
                  ],
                ),
              ),
              Expanded(
                child: FutureBuilder<List<ScheduledVisitAudit>>(
                  future: _auditFuture,
                  builder: (context, snapshot) {
                    if (snapshot.connectionState == ConnectionState.waiting) {
                      return const Center(child: CircularProgressIndicator());
                    }

                    if (snapshot.hasError) {
                      return Center(child: Text('Error: ${snapshot.error}'));
                    }

                    final audits = snapshot.data ?? [];

                    if (audits.isEmpty) {
                      return const Center(child: Text('No changes recorded'));
                    }

                    return ListView.builder(
                      controller: scrollController,
                      itemCount: audits.length,
                      itemBuilder: (context, index) {
                        final audit = audits[index];
                        return _buildAuditTile(audit);
                      },
                    );
                  },
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildAuditTile(ScheduledVisitAudit audit) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8),
      child: Card(
        child: ListTile(
          leading: Icon(
            _getActionIcon(audit.action),
            color: _getActionColor(audit.action),
          ),
          title: Text(
            audit.action.toUpperCase(),
            style: TextStyle(
              fontWeight: FontWeight.bold,
              color: _getActionColor(audit.action),
            ),
          ),
          subtitle: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '${DateFormat('MMM d, yyyy - kk:mm').format(audit.changedAt)}',
                style: const TextStyle(fontSize: 12),
              ),
              Text(
                'By: ${audit.changedBy}',
                style: const TextStyle(fontSize: 11, color: Colors.grey),
              ),
              if (audit.changedField != null && audit.changedField!.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: 4.0),
                  child: Text(
                    'Field: ${audit.changedField}',
                    style: const TextStyle(
                      fontSize: 11,
                      fontStyle: FontStyle.italic,
                    ),
                  ),
                ),
              if (audit.oldValue != null && audit.oldValue!.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: 4.0),
                  child: Text(
                    'From: ${audit.oldValue}',
                    style: const TextStyle(fontSize: 11, color: Colors.red),
                  ),
                ),
              if (audit.newValue != null && audit.newValue!.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: 2.0),
                  child: Text(
                    'To: ${audit.newValue}',
                    style: const TextStyle(fontSize: 11, color: Colors.green),
                  ),
                ),
            ],
          ),
          isThreeLine: true,
        ),
      ),
    );
  }
}
