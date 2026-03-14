import 'package:flutter/material.dart';
import 'package:care_connect_app/features/shift_scheduling/models/scheduled_visit_model.dart';
import 'package:care_connect_app/features/shift_scheduling/services/schedule_api_service.dart';
import 'month_calendar_view.dart';
import 'week_calendar_view.dart';
import 'day_schedule_view.dart';

class CalendarView extends StatefulWidget {
  final int caregiverId;
  final Function(ScheduledVisit)? onVisitTap;
  final Function(ScheduledVisit)? onVisitUpdate;

  const CalendarView({
    super.key,
    required this.caregiverId,
    this.onVisitTap,
    this.onVisitUpdate,
  });

  @override
  State<CalendarView> createState() => _CalendarViewState();
}

class _CalendarViewState extends State<CalendarView> {
  late ScheduleApiService _apiService;
  List<ScheduledVisit> _visits = [];
  bool _isLoading = false;
  String _viewMode = 'month'; // 'month', 'week', 'day'
  DateTime _selectedDate = DateTime.now();

  @override
  void initState() {
    super.initState();
    _apiService = ScheduleApiService();
    _loadSchedule();
  }

  Future<void> _loadSchedule() async {
    setState(() => _isLoading = true);

    try {
      List<ScheduledVisit> visits;
      if (_viewMode == 'month') {
        visits = await _apiService.getMonthSchedule(
          widget.caregiverId,
          _selectedDate.year,
          _selectedDate.month,
        );
      } else if (_viewMode == 'week') {
        final weekStart = _selectedDate.subtract(
          Duration(days: _selectedDate.weekday - 1),
        );
        visits = await _apiService.getWeekSchedule(
          widget.caregiverId,
          weekStart,
        );
      } else {
        visits = await _apiService.getDaySchedule(
          widget.caregiverId,
          _selectedDate,
        );
      }

      setState(() => _visits = visits);
    } catch (e) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Error loading schedule: $e')));
    } finally {
      setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Schedule Calendar'),
        elevation: 0,
        actions: [
          PopupMenuButton<String>(
            onSelected: (value) {
              setState(() => _viewMode = value);
              _loadSchedule();
            },
            itemBuilder: (BuildContext context) => [
              PopupMenuItem(
                value: 'month',
                child: Row(
                  children: [
                    Icon(_viewMode == 'month' ? Icons.check : null),
                    const SizedBox(width: 8),
                    const Text('Month View'),
                  ],
                ),
              ),
              PopupMenuItem(
                value: 'week',
                child: Row(
                  children: [
                    Icon(_viewMode == 'week' ? Icons.check : null),
                    const SizedBox(width: 8),
                    const Text('Week View'),
                  ],
                ),
              ),
              PopupMenuItem(
                value: 'day',
                child: Row(
                  children: [
                    Icon(_viewMode == 'day' ? Icons.check : null),
                    const SizedBox(width: 8),
                    const Text('Day View'),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _buildView(),
    );
  }

  Widget _buildView() {
    if (_viewMode == 'month') {
      return MonthCalendarView(
        caregiverId: widget.caregiverId,
        visits: _visits,
        selectedDate: _selectedDate,
        onDateSelected: (date) {
          setState(() => _selectedDate = date);
          _loadSchedule();
        },
        onVisitTap: widget.onVisitTap,
      );
    } else if (_viewMode == 'week') {
      return WeekCalendarView(
        caregiverId: widget.caregiverId,
        visits: _visits,
        selectedDate: _selectedDate,
        onDateSelected: (date) {
          setState(() => _selectedDate = date);
          _loadSchedule();
        },
        onVisitTap: widget.onVisitTap,
      );
    } else {
      return DayScheduleView(
        caregiverId: widget.caregiverId,
        visits: _visits,
        selectedDate: _selectedDate,
        onDateSelected: (date) {
          setState(() => _selectedDate = date);
          _loadSchedule();
        },
        onVisitTap: widget.onVisitTap,
      );
    }
  }
}
