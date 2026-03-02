import 'dart:convert';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../services/api_service.dart';

class PostCallTelemetrySummaryScreen extends StatefulWidget {
  final String callId;
  final String? recipientName;

  const PostCallTelemetrySummaryScreen({
    super.key,
    required this.callId,
    this.recipientName,
  });

  @override
  State<PostCallTelemetrySummaryScreen> createState() =>
      _PostCallTelemetrySummaryScreenState();
}

class _PostCallTelemetrySummaryScreenState
    extends State<PostCallTelemetrySummaryScreen> {
  bool _loading = true;
  bool _loadingRecordingExtract = false;
  List<Map<String, dynamic>> _callTelemetry = const [];
  Map<String, dynamic>? _recordingExtractInfo;
  String? _recordingExtractError;
  _TimelineChannel _selectedChannel = _TimelineChannel.all;

  @override
  void initState() {
    super.initState();
    _loadTelemetry();
  }

  @override
  void dispose() {
    super.dispose();
  }

  Future<void> _loadTelemetry() async {
    setState(() {
      _loading = true;
    });

    final callEvents = await ApiService.getCallTelemetry(widget.callId);
    final sorted = _sortByOccurredAtAsc(callEvents);

    Map<String, dynamic>? extractInfo;
    String? extractError;
    final patientUserId = _resolveRecordingPatientUserId(sorted);
    if (patientUserId != null) {
      setState(() {
        _loadingRecordingExtract = true;
      });
      extractInfo = await ApiService.getCallRecordingExtractInfo(
        callId: widget.callId,
        patientUserId: patientUserId,
      );
      if (extractInfo == null) {
        extractError = 'Unable to load recording extraction details.';
      }
    }

    if (!mounted) return;
    setState(() {
      _callTelemetry = sorted;
      _recordingExtractInfo = extractInfo;
      _recordingExtractError = extractError;
      _loadingRecordingExtract = false;
      _loading = false;
    });
  }

  int? _resolveRecordingPatientUserId(List<Map<String, dynamic>> events) {
    for (final event in events.reversed) {
      final eventType = (event['eventType'] as String?)?.toUpperCase() ?? '';
      if (eventType != 'CALL_RECORDING_START' &&
          eventType != 'CALL_RECORDING_STOP') {
        continue;
      }

      final targetUserId = event['targetUserId'];
      if (targetUserId is int) {
        return targetUserId;
      }
      final parsed = int.tryParse(targetUserId?.toString() ?? '');
      if (parsed != null) {
        return parsed;
      }
    }
    return null;
  }

  Future<void> _openRecordingExtractUrl() async {
    final info = _recordingExtractInfo;
    if (info == null) return;

    final rawUrl = (info['downloadUrl'] ?? '').toString().trim();
    if (rawUrl.isEmpty) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Recording URL is not available yet.')),
      );
      return;
    }

    final uri = Uri.tryParse(rawUrl);
    if (uri == null) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Recording URL is invalid.')),
      );
      return;
    }

    final launched = await launchUrl(uri, mode: LaunchMode.externalApplication);
    if (!launched && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Could not open recording download URL.')),
      );
    }
  }

  List<Map<String, dynamic>> _sortByOccurredAtAsc(
    List<Map<String, dynamic>> events,
  ) {
    final sorted = List<Map<String, dynamic>>.from(events);
    sorted.sort(
      (a, b) =>
          _safeDate(a['occurredAt']).compareTo(_safeDate(b['occurredAt'])),
    );
    return sorted;
  }

  DateTime _safeDate(dynamic input) {
    if (input is String) {
      return DateTime.tryParse(input) ?? DateTime.fromMillisecondsSinceEpoch(0);
    }
    return DateTime.fromMillisecondsSinceEpoch(0);
  }

  int get _sentimentEventCount => _callTelemetry
      .where(
        (event) =>
            (event['eventType'] as String?)?.toUpperCase().startsWith(
              'SENTIMENT_',
            ) ==
            true,
      )
      .length;

  String get _latestSentimentLabel {
    for (final event in _callTelemetry.reversed) {
      final label = (event['sentimentLabel'] as String?)?.trim();
      if (label != null && label.isNotEmpty) {
        return label;
      }
    }
    return '--';
  }

  String get _latestStatus {
    for (final event in _callTelemetry.reversed) {
      final status = (event['status'] as String?)?.trim();
      if (status != null && status.isNotEmpty) {
        return status;
      }
    }
    return '--';
  }

  String get _finalCallStatus {
    for (final event in _callTelemetry.reversed) {
      final eventType =
          (event['eventType'] as String?)?.trim().toUpperCase() ?? '';
      final status = (event['status'] as String?)?.trim().toUpperCase() ?? '';

      if (status == 'ERROR') {
        return 'Failed';
      }

      switch (eventType) {
        case 'WS_DECLINE_CALL':
          return 'Rejected';
        case 'WS_ACCEPT_CALL':
          return 'Accepted';
        case 'WS_END_CALL':
        case 'CALL_END':
          return 'Ended';
        case 'WS_SEND_VIDEO_CALL_INVITATION':
          return 'Invited';
        case 'CALL_JOIN':
          return 'Joined';
      }
    }

    return '--';
  }

  String get _finalCallWhy {
    final finalEvent = _resolveFinalOutcomeEvent();
    if (finalEvent == null) {
      return '--';
    }

    final eventType =
        (finalEvent['eventType'] as String?)?.trim().toUpperCase() ?? '';
    final status =
        (finalEvent['status'] as String?)?.trim().toUpperCase() ?? '';
    final errorMessage = (finalEvent['errorMessage'] as String?)?.trim();
    if (errorMessage != null && errorMessage.isNotEmpty) {
      return errorMessage;
    }

    final payloadReason = _extractReasonFromJsonBlob(finalEvent['payloadJson']);
    if (payloadReason != null) {
      return payloadReason;
    }

    if (status == 'ERROR') {
      return 'The call action failed to complete.';
    }

    switch (eventType) {
      case 'WS_DECLINE_CALL':
        return 'The recipient declined the invitation.';
      case 'WS_ACCEPT_CALL':
        return 'The recipient accepted and joined the call.';
      case 'WS_END_CALL':
      case 'CALL_END':
        return 'A participant ended the call.';
      case 'WS_SEND_VIDEO_CALL_INVITATION':
        return 'Invitation was sent to the recipient.';
      case 'CALL_JOIN':
        return 'Participant joined the call session.';
      default:
        return '--';
    }
  }

  String get _finalCallStatusWithReason {
    final status = _finalCallStatus;
    final why = _finalCallWhy;

    if (status == '--') {
      return status;
    }
    if (why == '--') {
      return status;
    }
    return '$status - $why';
  }

  Map<String, dynamic>? _resolveFinalOutcomeEvent() {
    for (final event in _callTelemetry.reversed) {
      final eventType =
          (event['eventType'] as String?)?.trim().toUpperCase() ?? '';
      final status = (event['status'] as String?)?.trim().toUpperCase() ?? '';

      if (status == 'ERROR') {
        return event;
      }

      if (eventType == 'WS_DECLINE_CALL' ||
          eventType == 'WS_ACCEPT_CALL' ||
          eventType == 'WS_END_CALL' ||
          eventType == 'CALL_END' ||
          eventType == 'WS_SEND_VIDEO_CALL_INVITATION' ||
          eventType == 'CALL_JOIN') {
        return event;
      }
    }
    return null;
  }

  String? _extractReasonFromJsonBlob(dynamic jsonBlob) {
    if (jsonBlob is! String || jsonBlob.trim().isEmpty) {
      return null;
    }

    try {
      final decoded = Map<String, dynamic>.from(
        (jsonDecode(jsonBlob) as Map).cast<String, dynamic>(),
      );
      final reason = (decoded['reason'] as String?)?.trim();
      if (reason != null && reason.isNotEmpty) {
        return reason;
      }
      final message = (decoded['message'] as String?)?.trim();
      if (message != null && message.isNotEmpty) {
        return message;
      }
    } catch (_) {
      return null;
    }
    return null;
  }

  DateTime? get _callStart {
    if (_callTelemetry.isEmpty) return null;
    return _safeDate(_callTelemetry.first['occurredAt']);
  }

  DateTime? get _callEnd {
    if (_callTelemetry.isEmpty) return null;
    return _safeDate(_callTelemetry.last['occurredAt']);
  }

  double get _callDurationMinutes {
    final start = _callStart;
    final end = _callEnd;
    if (start == null || end == null) return 0;
    final seconds = end.difference(start).inSeconds;
    if (seconds <= 0) return 1;
    return seconds / 60.0;
  }

  String get _callDurationText {
    final start = _callStart;
    final end = _callEnd;
    if (start == null || end == null) return '--';

    final diff = end.difference(start);
    final hours = diff.inHours;
    final minutes = diff.inMinutes.remainder(60).toString().padLeft(2, '0');
    final seconds = diff.inSeconds.remainder(60).toString().padLeft(2, '0');
    if (hours > 0) {
      return '$hours:$minutes:$seconds';
    }
    return '${diff.inMinutes}:$seconds';
  }

  _TimelineChannel _resolveEventChannel(Map<String, dynamic> event) {
    final channelRaw =
        (event['channel'] as String?)?.trim().toUpperCase() ?? '';
    if (channelRaw == 'TEXT') return _TimelineChannel.text;
    if (channelRaw == 'VOICE' || channelRaw == 'AUDIO') {
      return _TimelineChannel.voice;
    }
    if (channelRaw == 'COMBINED') return _TimelineChannel.video;

    final eventType =
        (event['eventType'] as String?)?.trim().toUpperCase() ?? '';
    if (eventType.contains('TEXT')) return _TimelineChannel.text;
    if (eventType.contains('VOICE') || eventType.contains('AUDIO')) {
      return _TimelineChannel.voice;
    }
    if (eventType.contains('COMBINED')) return _TimelineChannel.video;
    return _TimelineChannel.video;
  }

  Map<_TimelineChannel, List<_ScoreSample>> get _allChannelSeries {
    final start = _callStart;
    if (start == null) {
      return {
        _TimelineChannel.text: const <_ScoreSample>[],
        _TimelineChannel.voice: const <_ScoreSample>[],
        _TimelineChannel.video: const <_ScoreSample>[],
      };
    }

    final byChannel = <_TimelineChannel, List<_ScoreSample>>{
      _TimelineChannel.text: <_ScoreSample>[],
      _TimelineChannel.voice: <_ScoreSample>[],
      _TimelineChannel.video: <_ScoreSample>[],
    };

    for (final event in _callTelemetry) {
      final eventType = (event['eventType'] as String?)?.toUpperCase() ?? '';
      if (!eventType.startsWith('SENTIMENT_')) continue;

      final channel = _resolveEventChannel(event);
      if (!byChannel.containsKey(channel)) continue;

      final score = (event['sentimentScore'] as num?)?.toDouble();
      if (score == null) continue;

      final at = _safeDate(event['occurredAt']);
      final minuteOffset = at.difference(start).inMilliseconds / 60000.0;
      byChannel[channel]!.add(
        _ScoreSample(
          minuteOffset: minuteOffset.clamp(0.0, _callDurationMinutes),
          score: score.clamp(0.0, 1.0),
        ),
      );
    }

    for (final points in byChannel.values) {
      points.sort((a, b) => a.minuteOffset.compareTo(b.minuteOffset));
    }

    return byChannel;
  }

  Map<_TimelineChannel, List<_ScoreSample>> get _visibleSeries {
    final all = _allChannelSeries;
    if (_selectedChannel == _TimelineChannel.all) {
      return all;
    }
    return {_selectedChannel: all[_selectedChannel] ?? const <_ScoreSample>[]};
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Call Summary'),
        actions: [
          IconButton(
            onPressed: _loadTelemetry,
            tooltip: 'Refresh',
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: _loadTelemetry,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  if (widget.recipientName != null &&
                      widget.recipientName!.trim().isNotEmpty)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: Text(
                        'Call with ${widget.recipientName}',
                        style: theme.textTheme.titleMedium,
                      ),
                    ),
                  _SummaryCard(
                    callId: widget.callId,
                    totalEvents: _callTelemetry.length,
                    sentimentEvents: _sentimentEventCount,
                    latestSentiment: _latestSentimentLabel,
                    finalCallStatus: _finalCallStatusWithReason,
                    latestStatus: _latestStatus,
                    callDuration: _callDurationText,
                  ),
                  const SizedBox(height: 12),
                  _buildRecordingExtractionCard(),
                  const SizedBox(height: 16),
                  Text(
                    'Sentiment Timeline',
                    style: theme.textTheme.titleMedium,
                  ),
                  const SizedBox(height: 8),
                  _buildTimelineCard(),
                ],
              ),
            ),
    );
  }

  Widget _buildRecordingExtractionCard() {
    final info = _recordingExtractInfo;
    final isAvailable = info?['recordingAvailable'] == true;
    final status = (info?['status'] ?? '--').toString();
    final message =
        (info?['message'] ?? _recordingExtractError ?? 'No recording details available.')
            .toString();
    final fileName = (info?['fileName'] ?? '').toString();

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Call Recording',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 6),
            Text('Status: $status'),
            const SizedBox(height: 4),
            if (_loadingRecordingExtract)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 8),
                child: LinearProgressIndicator(minHeight: 2),
              )
            else
              Text(message),
            if (fileName.isNotEmpty) ...[
              const SizedBox(height: 4),
              Text('File: $fileName'),
            ],
            const SizedBox(height: 10),
            Align(
              alignment: Alignment.centerLeft,
              child: FilledButton.icon(
                onPressed: (!_loadingRecordingExtract && isAvailable)
                    ? _openRecordingExtractUrl
                    : null,
                icon: const Icon(Icons.download),
                label: const Text('Extract Recording'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTimelineCard() {
    final theme = Theme.of(context);
    final duration = _callDurationMinutes;
    final allSeries = _allChannelSeries;
    final visibleSeries = _visibleSeries;
    final hasAnySamples = allSeries.values.any((points) => points.isNotEmpty);
    final hasSelectedSamples = visibleSeries.values.any(
      (points) => points.isNotEmpty,
    );

    if (_callTelemetry.isEmpty) {
      return Card(
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Text(
            'No telemetry saved for this call yet.',
            style: theme.textTheme.bodyMedium,
          ),
        ),
      );
    }

    final channelColors = <_TimelineChannel, Color>{
      _TimelineChannel.text: const Color(0xFF00BCD4),
      _TimelineChannel.voice: const Color(0xFFFF9800),
      _TimelineChannel.video: const Color(0xFF9C27B0),
    };

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: LayoutBuilder(
          builder: (context, constraints) {
            final viewportWidth = math.max(320.0, constraints.maxWidth);
            final baseTimelineWidth = math.max(viewportWidth, duration * 120.0);
            final contentWidth = math
                .min(24000.0, baseTimelineWidth)
                .toDouble();

            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Bands represent score zones (Positive/Neutral/Negative). Colored lines plot actual score values at each timestamp.',
                  style: theme.textTheme.bodySmall,
                ),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: _TimelineChannel.values.map((channel) {
                    return ChoiceChip(
                      label: Text(channel.label),
                      selected: _selectedChannel == channel,
                      onSelected: (_) {
                        setState(() {
                          _selectedChannel = channel;
                        });
                      },
                    );
                  }).toList(),
                ),
                if (!hasAnySamples)
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Text(
                      'No score-based sentiment samples were captured for this call yet.',
                      style: theme.textTheme.bodyMedium,
                    ),
                  )
                else if (!hasSelectedSamples)
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Text(
                      'No samples for ${_selectedChannel.label}. Select another channel to continue.',
                      style: theme.textTheme.bodyMedium,
                    ),
                  ),
                const SizedBox(height: 8),
                SizedBox(
                  height: 300,
                  child: SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: SizedBox(
                      width: contentWidth,
                      child: CustomPaint(
                        painter: _SentimentTimelinePainter(
                          durationMinutes: duration,
                          series: visibleSeries,
                          channelColors: channelColors,
                        ),
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                _TimelineLegend(channelColors: channelColors),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _SummaryCard extends StatelessWidget {
  final String callId;
  final int totalEvents;
  final int sentimentEvents;
  final String latestSentiment;
  final String finalCallStatus;
  final String latestStatus;
  final String callDuration;

  const _SummaryCard({
    required this.callId,
    required this.totalEvents,
    required this.sentimentEvents,
    required this.latestSentiment,
    required this.finalCallStatus,
    required this.latestStatus,
    required this.callDuration,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Call ID: $callId'),
            const SizedBox(height: 6),
            Text('Total telemetry events: $totalEvents'),
            Text('Sentiment events: $sentimentEvents'),
            Text('Call duration: $callDuration'),
            Text('Latest sentiment label: $latestSentiment'),
            Text('Final call status: $finalCallStatus'),
            Text('Latest event status: $latestStatus'),
          ],
        ),
      ),
    );
  }
}

enum _TimelineChannel {
  all('All'),
  text('Text'),
  voice('Voice'),
  video('Video');

  final String label;
  const _TimelineChannel(this.label);
}

class _ScoreSample {
  final double minuteOffset;
  final double score;

  const _ScoreSample({required this.minuteOffset, required this.score});
}

class _SentimentTimelinePainter extends CustomPainter {
  final double durationMinutes;
  final Map<_TimelineChannel, List<_ScoreSample>> series;
  final Map<_TimelineChannel, Color> channelColors;

  const _SentimentTimelinePainter({
    required this.durationMinutes,
    required this.series,
    required this.channelColors,
  });

  @override
  void paint(Canvas canvas, Size size) {
    const leftPad = 64.0;
    const rightPad = 20.0;
    const topPad = 20.0;
    const bottomPad = 44.0;

    final plotWidth = math.max(1.0, size.width - leftPad - rightPad);
    final plotHeight = math.max(1.0, size.height - topPad - bottomPad);
    final plotRect = Rect.fromLTWH(leftPad, topPad, plotWidth, plotHeight);

    final framePaint = Paint()
      ..color = Colors.grey.shade400
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1;
    canvas.drawRect(plotRect, framePaint);

    final textPainter = TextPainter(textDirection: TextDirection.ltr);
    double yForScore(double score) {
      return plotRect.bottom - score.clamp(0.0, 1.0) * plotRect.height;
    }

    final positiveTop = yForScore(1.0);
    final positiveBottom = yForScore(0.6);
    final neutralTop = yForScore(0.6);
    final neutralBottom = yForScore(0.4);
    final negativeTop = yForScore(0.4);
    final negativeBottom = yForScore(0.0);

    canvas.drawRect(
      Rect.fromLTRB(plotRect.left, positiveTop, plotRect.right, positiveBottom),
      Paint()..color = Colors.green.withValues(alpha: 0.12),
    );
    canvas.drawRect(
      Rect.fromLTRB(plotRect.left, neutralTop, plotRect.right, neutralBottom),
      Paint()..color = Colors.amber.withValues(alpha: 0.12),
    );
    canvas.drawRect(
      Rect.fromLTRB(plotRect.left, negativeTop, plotRect.right, negativeBottom),
      Paint()..color = Colors.red.withValues(alpha: 0.12),
    );

    _drawBandGuideline(
      canvas,
      y: (positiveTop + positiveBottom) / 2,
      color: Colors.green,
      leftPad: leftPad,
      rightX: leftPad + plotWidth,
    );
    _drawBandGuideline(
      canvas,
      y: (neutralTop + neutralBottom) / 2,
      color: Colors.amber.shade700,
      leftPad: leftPad,
      rightX: leftPad + plotWidth,
    );
    _drawBandGuideline(
      canvas,
      y: (negativeTop + negativeBottom) / 2,
      color: Colors.red,
      leftPad: leftPad,
      rightX: leftPad + plotWidth,
    );

    for (final entry in series.entries) {
      final points = entry.value;
      if (points.isEmpty) continue;

      final color = channelColors[entry.key] ?? Colors.blueGrey;
      final linePaint = Paint()
        ..color = color
        ..strokeWidth = 2.2
        ..style = PaintingStyle.stroke;

      final path = Path();
      for (var i = 0; i < points.length; i++) {
        final sample = points[i];
        final x = leftPad + (sample.minuteOffset / durationMinutes) * plotWidth;
        final y = yForScore(sample.score);
        if (i == 0) {
          path.moveTo(x, y);
        } else {
          path.lineTo(x, y);
        }

        canvas.drawCircle(Offset(x, y), 3.5, Paint()..color = color);
      }

      canvas.drawPath(path, linePaint);
    }

    const tickCount = 5;
    for (var i = 0; i < tickCount; i++) {
      final ratio = i / (tickCount - 1);
      final x = leftPad + ratio * plotWidth;
      final tickMinute = durationMinutes * ratio;
      canvas.drawLine(
        Offset(x, plotRect.bottom),
        Offset(x, plotRect.bottom + 6),
        Paint()..color = Colors.grey.shade500,
      );

      textPainter.text = TextSpan(
        text: _formatMinuteTick(tickMinute),
        style: TextStyle(color: Colors.grey.shade700, fontSize: 11),
      );
      textPainter.layout();
      textPainter.paint(
        canvas,
        Offset(x - textPainter.width / 2, plotRect.bottom + 10),
      );
    }

    textPainter.text = TextSpan(
      text: 'Elapsed Time',
      style: TextStyle(color: Colors.grey.shade700, fontSize: 12),
    );
    textPainter.layout();
    textPainter.paint(
      canvas,
      Offset(
        leftPad + plotWidth / 2 - textPainter.width / 2,
        size.height - textPainter.height,
      ),
    );
  }

  void _drawBandGuideline(
    Canvas canvas, {
    required double y,
    required Color color,
    required double leftPad,
    required double rightX,
  }) {
    final paint = Paint()
      ..color = color.withValues(alpha: 0.6)
      ..strokeWidth = 1;
    canvas.drawLine(Offset(leftPad, y), Offset(rightX, y), paint);
  }

  String _formatMinuteTick(double minute) {
    final totalSeconds = (minute * 60).round();
    final hours = totalSeconds ~/ 3600;
    final minutes = (totalSeconds % 3600) ~/ 60;
    final seconds = totalSeconds % 60;
    if (hours > 0) {
      return '${hours}h ${minutes}m';
    }
    if (minutes > 0) {
      return '${minutes}m ${seconds}s';
    }
    return '${seconds}s';
  }

  @override
  bool shouldRepaint(covariant _SentimentTimelinePainter oldDelegate) {
    return oldDelegate.durationMinutes != durationMinutes ||
        oldDelegate.series != series ||
        oldDelegate.channelColors != channelColors;
  }
}

class _TimelineLegend extends StatelessWidget {
  final Map<_TimelineChannel, Color> channelColors;

  const _TimelineLegend({required this.channelColors});

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 12,
      runSpacing: 8,
      children: [
        const _LegendChip(label: 'Positive zone', color: Colors.green),
        const _LegendChip(label: 'Neutral zone', color: Colors.amber),
        const _LegendChip(label: 'Negative zone', color: Colors.red),
        _LegendChip(
          label: 'Text line',
          color: channelColors[_TimelineChannel.text] ?? Colors.blueGrey,
        ),
        _LegendChip(
          label: 'Voice line',
          color: channelColors[_TimelineChannel.voice] ?? Colors.blueGrey,
        ),
        _LegendChip(
          label: 'Video line',
          color: channelColors[_TimelineChannel.video] ?? Colors.blueGrey,
        ),
      ],
    );
  }
}

class _LegendChip extends StatelessWidget {
  final String label;
  final Color color;

  const _LegendChip({required this.label, required this.color});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 12,
          height: 12,
          decoration: BoxDecoration(
            color: color,
            borderRadius: BorderRadius.circular(3),
          ),
        ),
        const SizedBox(width: 6),
        Text(label),
      ],
    );
  }
}
