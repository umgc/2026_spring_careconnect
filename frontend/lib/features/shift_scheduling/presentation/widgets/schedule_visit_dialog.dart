import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:care_connect_app/features/shift_scheduling/models/scheduled_visit_model.dart';
import 'package:care_connect_app/features/shift_scheduling/services/schedule_api_service.dart';
import 'conflict_warning.dart';

class ScheduleVisitDialog extends StatefulWidget {
  final int caregiverId;
  final ScheduledVisit? existingVisit;
  final Function(ScheduledVisit)? onSave;

  const ScheduleVisitDialog({
    super.key,
    required this.caregiverId,
    this.existingVisit,
    this.onSave,
  });

  @override
  State<ScheduleVisitDialog> createState() => _ScheduleVisitDialogState();
}

class _ScheduleVisitDialogState extends State<ScheduleVisitDialog> {
  late TextEditingController _patientController;
  late TextEditingController _serviceTypeController;
  late TextEditingController _notesController;
  late ScheduleApiService _apiService;
  DateTime? _selectedDate;
  TimeOfDay? _selectedTime;
  String _priority = 'Normal';
  int _duration = 60;
  VisitConflict? _conflict;
  bool _ignoreConflict = false;
  bool _isChecking = false;

  final List<String> _priorityOptions = ['Low', 'Normal', 'Medium', 'High'];
  final List<int> _durationOptions = [15, 30, 45, 60, 90, 120];

  @override
  void initState() {
    super.initState();
    _apiService = ScheduleApiService();
    _patientController = TextEditingController(
      text: widget.existingVisit?.patientName ?? '',
    );
    _serviceTypeController = TextEditingController(
      text: widget.existingVisit?.serviceType ?? '',
    );
    _notesController = TextEditingController(
      text: widget.existingVisit?.notes ?? '',
    );
    _selectedDate = widget.existingVisit?.scheduledDate ?? DateTime.now();
    _selectedTime = widget.existingVisit?.scheduledTime ?? TimeOfDay.now();
    _priority = widget.existingVisit?.priority ?? 'Normal';
    _duration = widget.existingVisit?.durationMinutes ?? 60;
  }

  @override
  void dispose() {
    _patientController.dispose();
    _serviceTypeController.dispose();
    _notesController.dispose();
    super.dispose();
  }

  Future<void> _checkConflicts() async {
    if (_selectedDate == null || _selectedTime == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please select date and time')),
      );
      return;
    }

    setState(() => _isChecking = true);

    try {
      final visitRequest = {
        'patientId': 0, // Replace with actual patient ID
        'serviceType': _serviceTypeController.text,
        'scheduledDate': _selectedDate.toString().split(' ')[0],
        'scheduledTime':
            '${_selectedTime!.hour.toString().padLeft(2, '0')}:${_selectedTime!.minute.toString().padLeft(2, '0')}',
        'durationMinutes': _duration,
      };

      final conflict = await _apiService.checkConflicts(
        widget.caregiverId,
        visitRequest,
      );

      setState(() => _conflict = conflict);
    } catch (e) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Error checking conflicts: $e')));
    } finally {
      setState(() => _isChecking = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      insetPadding: const EdgeInsets.all(16),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // Header
            Padding(
              padding: const EdgeInsets.all(16.0),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    widget.existingVisit == null
                        ? 'Schedule New Visit'
                        : 'Update Visit',
                    style: Theme.of(context).textTheme.headlineSmall,
                  ),
                  IconButton(
                    icon: const Icon(Icons.close),
                    onPressed: () => Navigator.pop(context),
                  ),
                ],
              ),
            ),

            // Content
            Padding(
              padding: const EdgeInsets.all(16.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Conflict Warning
                  if (_conflict != null && !_ignoreConflict)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 16.0),
                      child: ConflictWarning(
                        conflict: _conflict!,
                        onDismiss: () {
                          setState(() => _ignoreConflict = true);
                        },
                      ),
                    ),

                  // Patient Name
                  _buildTextField('Patient Name', _patientController),

                  // Service Type
                  _buildTextField('Service Type', _serviceTypeController),

                  // Date Picker
                  _buildDatePicker(),

                  // Time Picker
                  _buildTimePicker(),

                  // Duration Dropdown
                  _buildDurationDropdown(),

                  // Priority Dropdown
                  _buildPriorityDropdown(),

                  // Notes
                  _buildTextField('Notes', _notesController, maxLines: 3),

                  // Check Conflicts Button
                  Padding(
                    padding: const EdgeInsets.only(top: 16.0, bottom: 16.0),
                    child: SizedBox(
                      width: double.infinity,
                      child: ElevatedButton.icon(
                        onPressed: _isChecking ? null : _checkConflicts,
                        icon: const Icon(Icons.check_circle),
                        label: _isChecking
                            ? const SizedBox(
                                width: 16,
                                height: 16,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                ),
                              )
                            : const Text('Check for Conflicts'),
                      ),
                    ),
                  ),
                ],
              ),
            ),

            // Action Buttons
            Padding(
              padding: const EdgeInsets.all(16.0),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  TextButton(
                    onPressed: () => Navigator.pop(context),
                    child: const Text('Cancel'),
                  ),
                  const SizedBox(width: 8),
                  ElevatedButton(
                    onPressed: () {
                      if (_validateForm()) {
                        final visit = ScheduledVisit(
                          id: widget.existingVisit?.id ?? 0,
                          caregiverId: widget.caregiverId,
                          patientId: 0, // Replace with actual patient ID
                          patientName: _patientController.text,
                          serviceType: _serviceTypeController.text,
                          scheduledDate: _selectedDate!,
                          scheduledTime: _selectedTime!,
                          durationMinutes: _duration,
                          priority: _priority,
                          notes: _notesController.text.isEmpty
                              ? null
                              : _notesController.text,
                          status: widget.existingVisit?.status ?? 'Scheduled',
                          createdAt:
                              widget.existingVisit?.createdAt ?? DateTime.now(),
                          updatedAt: DateTime.now(),
                        );
                        widget.onSave?.call(visit);
                        Navigator.pop(context);
                      }
                    },
                    child: Text(
                      widget.existingVisit == null ? 'Create' : 'Update',
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTextField(
    String label,
    TextEditingController controller, {
    int maxLines = 1,
  }) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16.0),
      child: TextField(
        controller: controller,
        maxLines: maxLines,
        decoration: InputDecoration(
          labelText: label,
          border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
        ),
      ),
    );
  }

  Widget _buildDatePicker() {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16.0),
      child: InkWell(
        onTap: () async {
          final date = await showDatePicker(
            context: context,
            initialDate: _selectedDate ?? DateTime.now(),
            firstDate: DateTime.now(),
            lastDate: DateTime.now().add(const Duration(days: 365)),
          );
          if (date != null) {
            setState(() => _selectedDate = date);
          }
        },
        child: InputDecorator(
          decoration: InputDecoration(
            labelText: 'Date',
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                _selectedDate == null
                    ? 'Select Date'
                    : DateFormat('MMM d, yyyy').format(_selectedDate!),
              ),
              const Icon(Icons.calendar_today),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildTimePicker() {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16.0),
      child: InkWell(
        onTap: () async {
          final time = await showTimePicker(
            context: context,
            initialTime: _selectedTime ?? TimeOfDay.now(),
          );
          if (time != null) {
            setState(() => _selectedTime = time);
          }
        },
        child: InputDecorator(
          decoration: InputDecoration(
            labelText: 'Time',
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                _selectedTime == null
                    ? 'Select Time'
                    : _selectedTime!.format(context),
              ),
              const Icon(Icons.access_time),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildDurationDropdown() {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16.0),
      child: DropdownButtonFormField<int>(
        value: _duration,
        decoration: InputDecoration(
          labelText: 'Duration (minutes)',
          border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
        ),
        items: _durationOptions.map((duration) {
          return DropdownMenuItem(
            value: duration,
            child: Text('$duration minutes'),
          );
        }).toList(),
        onChanged: (value) {
          if (value != null) {
            setState(() => _duration = value);
          }
        },
      ),
    );
  }

  Widget _buildPriorityDropdown() {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16.0),
      child: DropdownButtonFormField<String>(
        value: _priority,
        decoration: InputDecoration(
          labelText: 'Priority',
          border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
        ),
        items: _priorityOptions.map((priority) {
          return DropdownMenuItem(value: priority, child: Text(priority));
        }).toList(),
        onChanged: (value) {
          if (value != null) {
            setState(() => _priority = value);
          }
        },
      ),
    );
  }

  bool _validateForm() {
    if (_patientController.text.isEmpty) {
      _showError('Please enter patient name');
      return false;
    }
    if (_serviceTypeController.text.isEmpty) {
      _showError('Please enter service type');
      return false;
    }
    if (_selectedDate == null) {
      _showError('Please select a date');
      return false;
    }
    if (_selectedTime == null) {
      _showError('Please select a time');
      return false;
    }
    if (_conflict != null && !_ignoreConflict) {
      _showError('Please resolve conflicts before saving');
      return false;
    }
    return true;
  }

  void _showError(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message), backgroundColor: Colors.red),
    );
  }
}
