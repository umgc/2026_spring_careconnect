import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:care_connect_app/features/shift_scheduling/models/scheduled_visit_model.dart';

class DayScheduleView extends StatefulWidget {
  final int caregiverId;
  final List<ScheduledVisit> visits;
  final DateTime selectedDate;
  final Function(DateTime)? onDateSelected;
  final Function(ScheduledVisit)? onVisitTap;

  const DayScheduleView({
    super.key,
    required this.caregiverId,
    required this.visits,
    required this.selectedDate,
    this.onDateSelected,
    this.onVisitTap,
  });

  @override
  State<DayScheduleView> createState() => _DayScheduleViewState();
}

class _DayScheduleViewState extends State<DayScheduleView> {
  final ScrollController _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    // Scroll to current time on init
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _scrollToCurrentTime();
    });
  }

  void _scrollToCurrentTime() {
    final now = DateTime.now();
    final minute = now.hour * 60 + now.minute;
    final offset = (minute / 60) * 80; // Approximate pixel offset
    _scrollController.jumpTo(offset);
  }

  @override
  Widget build(BuildContext context) {
    final List<ScheduledVisit> sortedVisits = List.from(widget.visits)
      ..sort((a, b) {
        final aMinutes = a.scheduledTime.hour * 60 + a.scheduledTime.minute;
        final bMinutes = b.scheduledTime.hour * 60 + b.scheduledTime.minute;
        return aMinutes.compareTo(bMinutes);
      });

    return SingleChildScrollView(
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                IconButton(
                  icon: const Icon(Icons.chevron_left),
                  onPressed: () {
                    widget.onDateSelected?.call(
                      widget.selectedDate.subtract(const Duration(days: 1)),
                    );
                  },
                ),
                Text(
                  DateFormat('EEEE, MMMM d, yyyy').format(widget.selectedDate),
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                IconButton(
                  icon: const Icon(Icons.chevron_right),
                  onPressed: () {
                    widget.onDateSelected?.call(
                      widget.selectedDate.add(const Duration(days: 1)),
                    );
                  },
                ),
              ],
            ),
          ),
          _buildTimeSlots(sortedVisits),
        ],
      ),
    );
  }

  Widget _buildTimeSlots(List<ScheduledVisit> visits) {
    return SizedBox(
      height: MediaQuery.of(context).size.height * 0.8,
      child: SingleChildScrollView(
        controller: _scrollController,
        child: Column(
          children: List.generate(24, (hour) {
            final visitsAtHour = visits.where((visit) {
              return visit.scheduledTime.hour == hour;
            }).toList();

            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 16.0,
                    vertical: 8.0,
                  ),
                  child: Row(
                    children: [
                      SizedBox(
                        width: 50,
                        child: Text(
                          '${hour.toString().padLeft(2, '0')}:00',
                          style: const TextStyle(
                            fontWeight: FontWeight.bold,
                            fontSize: 12,
                          ),
                        ),
                      ),
                      Expanded(
                        child: Container(
                          height: 1,
                          color: Colors.grey.shade300,
                        ),
                      ),
                    ],
                  ),
                ),
                ...visitsAtHour.map((visit) {
                  return Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 16.0,
                      vertical: 4.0,
                    ),
                    child: GestureDetector(
                      onTap: () => widget.onVisitTap?.call(visit),
                      child: Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: visit.getPriorityColor().withOpacity(0.15),
                          border: Border(
                            left: BorderSide(
                              color: visit.getPriorityColor(),
                              width: 4,
                            ),
                          ),
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              mainAxisAlignment: MainAxisAlignment.spaceBetween,
                              children: [
                                Expanded(
                                  child: Text(
                                    visit.patientName,
                                    style: const TextStyle(
                                      fontWeight: FontWeight.bold,
                                    ),
                                  ),
                                ),
                                Container(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 6,
                                    vertical: 2,
                                  ),
                                  decoration: BoxDecoration(
                                    color: visit.getPriorityColor(),
                                    borderRadius: BorderRadius.circular(3),
                                  ),
                                  child: Text(
                                    visit.priority,
                                    style: const TextStyle(
                                      fontSize: 10,
                                      color: Colors.white,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                            Text(
                              visit.serviceType,
                              style: const TextStyle(fontSize: 12),
                            ),
                            Text(
                              '${visit.scheduledTime.format(context)} - ${visit.getEndTime()} (${visit.durationMinutes} min)',
                              style: const TextStyle(
                                fontSize: 11,
                                color: Colors.grey,
                              ),
                            ),
                            if (visit.notes != null && visit.notes!.isNotEmpty)
                              Padding(
                                padding: const EdgeInsets.only(top: 4.0),
                                child: Text(
                                  'Notes: ${visit.notes}',
                                  style: const TextStyle(
                                    fontSize: 10,
                                    fontStyle: FontStyle.italic,
                                  ),
                                  maxLines: 2,
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                          ],
                        ),
                      ),
                    ),
                  );
                }).toList(),
              ],
            );
          }),
        ),
      ),
    );
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }
}
