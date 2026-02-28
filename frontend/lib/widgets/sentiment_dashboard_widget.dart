import 'dart:math' as math;
import 'package:flutter/material.dart';

/// SentimentDashboardWidget — live emotional analysis panel during video calls.
///
/// Summary view: three horizontal bar graphs side by side (Text, Voice, Video)
/// Each bar is tappable and navigates to a detailed drill-down view showing
/// the full history of that channel's scores as a line graph.
///
/// Color coding:
///   0.0 - 0.3  →  red      (DISTRESSED / NEGATIVE)
///   0.3 - 0.55 →  amber    (ANXIOUS / NEUTRAL)
///   0.55 - 1.0 →  green    (CALM / POSITIVE)
class SentimentDashboardWidget extends StatefulWidget {
  final Map<String, dynamic> sentimentData;
  final String callId;
  final Future<void> Function(String text)? onTextSend;

  const SentimentDashboardWidget({
    super.key,
    required this.sentimentData,
    required this.callId,
    this.onTextSend,
  });

  @override
  State<SentimentDashboardWidget> createState() =>
      _SentimentDashboardWidgetState();
}

class _SentimentDashboardWidgetState extends State<SentimentDashboardWidget>
    with SingleTickerProviderStateMixin {
  // History for the detail charts — up to 30 data points per channel
  final List<_SentimentPoint> _textHistory  = [];
  final List<_SentimentPoint> _voiceHistory = [];
  final List<_SentimentPoint> _videoHistory = [];

  late AnimationController _animController;
  final TextEditingController _chatController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _animController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 600),
    );
  }

  @override
  void didUpdateWidget(SentimentDashboardWidget old) {
    super.didUpdateWidget(old);
    // When new sentiment data arrives, record it in history
    if (widget.sentimentData != old.sentimentData &&
        widget.sentimentData.isNotEmpty) {
      _recordHistory();
      _animController.forward(from: 0);
    }
  }

  void _recordHistory() {
    final now = DateTime.now();
    final text  = widget.sentimentData['text'];
    final voice = widget.sentimentData['voice'];
    final video = widget.sentimentData['video'];

    bool isCompleted(dynamic section) {
      if (section is! Map<String, dynamic>) return false;
      return ((section['status'] as String?) ?? 'COMPLETED').toUpperCase() ==
          'COMPLETED';
    }

    if (isCompleted(text)) {
      _textHistory.add(_SentimentPoint(
          now,
          ((text as Map<String, dynamic>)['score'] as num).toDouble(),
          ((text)['label'] as String?) ?? 'NEUTRAL'));
    }
    if (isCompleted(voice)) {
      _voiceHistory.add(_SentimentPoint(
          now,
          ((voice as Map<String, dynamic>)['score'] as num).toDouble(),
          ((voice)['label'] as String?) ?? 'NEUTRAL'));
    }
    if (isCompleted(video)) {
      _videoHistory.add(_SentimentPoint(
          now,
          ((video as Map<String, dynamic>)['score'] as num).toDouble(),
          ((video)['label'] as String?) ?? 'NEUTRAL'));
    }

    // Keep last 30 points
    if (_textHistory .length > 30) _textHistory .removeAt(0);
    if (_voiceHistory.length > 30) _voiceHistory.removeAt(0);
    if (_videoHistory.length > 30) _videoHistory.removeAt(0);
  }

  @override
  void dispose() {
    _animController.dispose();
    _chatController.dispose();
    super.dispose();
  }

  // ================================================================
  // EXTRACT SCORES from sentiment data payload
  // ================================================================

  double _score(String channel) {
    final data = widget.sentimentData[channel.toLowerCase()];
    if (data == null) return 0.5;
    return (data['score'] as num?)?.toDouble() ?? 0.5;
  }

  String _status(String channel) {
    final data = widget.sentimentData[channel.toLowerCase()];
    if (data == null) return 'AWAITING';
    return ((data['status'] as String?) ?? 'COMPLETED').toUpperCase();
  }

  String _label(String channel) {
    final data = widget.sentimentData[channel.toLowerCase()];
    if (data == null) return 'NEUTRAL';
    return (data['label'] as String?) ?? 'NEUTRAL';
  }

  String _notes(String channel) {
    final data = widget.sentimentData[channel.toLowerCase()];
    if (data == null) return '—';
    return (data['notes'] as String?) ?? '—';
  }

  double _overallScore() {
    final overall = widget.sentimentData['overall'];
    if (overall == null) return 0.5;
    return (overall['score'] as num?)?.toDouble() ?? 0.5;
  }

  String _overallLabel() {
    final overall = widget.sentimentData['overall'];
    if (overall == null) return 'NEUTRAL';
    return (overall['label'] as String?) ?? 'NEUTRAL';
  }

  String _overallStatus() {
    final overall = widget.sentimentData['overall'];
    if (overall == null) return 'AWAITING';
    return ((overall['status'] as String?) ?? 'COMPLETED').toUpperCase();
  }

  // ================================================================
  // BUILD
  // ================================================================

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final panelBg = isDark ? const Color(0xFF1A1A2E) : const Color(0xFFF5F7FA);
    final borderColor = isDark ? Colors.white12 : Colors.black12;

    return Container(
      decoration: BoxDecoration(
        color: panelBg,
        border: Border(top: BorderSide(color: borderColor, width: 1)),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          _buildPanelHeader(isDark),
          _buildBarGraphRow(isDark),
          _buildOverallScore(isDark),
          _buildChatInput(isDark),
        ],
      ),
    );
  }

  // ================================================================
  // PANEL HEADER — title + last updated timestamp
  // ================================================================

  Widget _buildPanelHeader(bool isDark) {
    final hasData = widget.sentimentData.isNotEmpty;
    final overallStatus = _overallStatus();
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 10, 16, 4),
      child: Row(
        children: [
          Icon(Icons.monitor_heart,
              size: 16,
              color: isDark ? Colors.tealAccent : Colors.teal.shade700),
          const SizedBox(width: 6),
          Text(
            'Live Emotional Analysis',
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w600,
              letterSpacing: 0.5,
              color: isDark ? Colors.white70 : Colors.black54,
            ),
          ),
          const Spacer(),
          if (hasData)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
              decoration: BoxDecoration(
                color:
                  _statusColor(overallStatus, _overallScore()).withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(10),
                border: Border.all(
                    color: _statusColor(overallStatus, _overallScore())
                      .withValues(alpha: 0.4)),
              ),
              child: Text(
                overallStatus == 'COMPLETED'
                    ? _overallLabel()
                    : overallStatus,
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 0.8,
                  color: _statusColor(overallStatus, _overallScore()),
                ),
              ),
            )
          else
            Text('Awaiting data...',
                style: TextStyle(
                    fontSize: 11,
                    color: isDark ? Colors.white38 : Colors.black38)),
        ],
      ),
    );
  }

  // ================================================================
  // THREE BAR GRAPHS — side by side, each tappable
  // ================================================================

  Widget _buildBarGraphRow(bool isDark) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      child: Row(
        children: [
          Expanded(child: _buildChannelBar(
            channel: 'TEXT',
            icon: Icons.chat_bubble_outline,
            history: _textHistory,
            isDark: isDark,
          )),
          const SizedBox(width: 8),
          Expanded(child: _buildChannelBar(
            channel: 'VOICE',
            icon: Icons.mic_none,
            history: _voiceHistory,
            isDark: isDark,
          )),
          const SizedBox(width: 8),
          Expanded(child: _buildChannelBar(
            channel: 'VIDEO',
            icon: Icons.videocam_outlined,
            history: _videoHistory,
            isDark: isDark,
          )),
        ],
      ),
    );
  }

  Widget _buildChannelBar({
    required String channel,
    required IconData icon,
    required List<_SentimentPoint> history,
    required bool isDark,
  }) {
    final score = _score(channel);
    final status = _status(channel);
    final hasUsableSample = status == 'COMPLETED';
    final label = hasUsableSample ? _label(channel) : status;
    final notes = hasUsableSample
        ? _notes(channel)
        : (status == 'DEGRADED'
            ? 'Sentiment temporarily unavailable; call continues normally.'
            : 'Awaiting channel sample.');
    final color = _statusColor(status, score);
    final cardBg = isDark ? const Color(0xFF252540) : Colors.white;

    return GestureDetector(
      onTap: () => _openDetailView(channel, history, icon),
      child: AnimatedBuilder(
        animation: _animController,
        builder: (context, child) => child!,
        child: Container(
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: cardBg,
            borderRadius: BorderRadius.circular(10),
            border: Border.all(color: color.withValues(alpha: 0.3), width: 1),
            boxShadow: [
              BoxShadow(
                color: color.withValues(alpha: 0.08),
                blurRadius: 8,
                offset: const Offset(0, 2),
              ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Channel label + icon
              Row(
                children: [
                  Icon(icon, size: 13, color: color),
                  const SizedBox(width: 4),
                  Text(
                    channel,
                    style: TextStyle(
                      fontSize: 10,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 0.6,
                      color: isDark ? Colors.white60 : Colors.black54,
                    ),
                  ),
                  const Spacer(),
                  Icon(Icons.chevron_right,
                      size: 12,
                      color: isDark ? Colors.white30 : Colors.black26),
                ],
              ),
              const SizedBox(height: 8),

              // Horizontal bar
              ClipRRect(
                borderRadius: BorderRadius.circular(4),
                child: Stack(
                  children: [
                    // Background track
                    Container(
                      height: 8,
                        color: isDark
                          ? Colors.white10
                          : Colors.black.withValues(alpha: 0.08),
                    ),
                    // Filled bar
                    FractionallySizedBox(
                      widthFactor: hasUsableSample ? score.clamp(0.0, 1.0) : 0.0,
                      child: Container(
                        height: 8,
                        decoration: BoxDecoration(
                          gradient: LinearGradient(
                            colors: [
                              color.withValues(alpha: 0.7),
                              color,
                            ],
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 6),

              // Score percentage
              Text(
                hasUsableSample
                    ? '${(score * 100).toStringAsFixed(0)}%'
                    : '—',
                style: TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.w700,
                  color: color,
                  height: 1.0,
                ),
              ),
              const SizedBox(height: 2),

              // Label
              Text(
                label,
                style: TextStyle(
                  fontSize: 9,
                  fontWeight: FontWeight.w600,
                  letterSpacing: 0.5,
                  color: color,
                ),
              ),
              const SizedBox(height: 4),

              // Clinical notes
              Text(
                notes,
                style: TextStyle(
                  fontSize: 9,
                  color: isDark ? Colors.white38 : Colors.black38,
                  fontStyle: FontStyle.italic,
                ),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
            ],
          ),
        ),
      ),
    );
  }

  // ================================================================
  // OVERALL SCORE BAR
  // ================================================================

  Widget _buildOverallScore(bool isDark) {
    final score = _overallScore();
    final status = _overallStatus();
    final hasUsableSample = status == 'COMPLETED' || status == 'DEGRADED';
    final color = _statusColor(status, score);

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      child: Row(
        children: [
          Text(
            'OVERALL',
            style: TextStyle(
              fontSize: 9,
              fontWeight: FontWeight.w700,
              letterSpacing: 0.8,
              color: isDark ? Colors.white38 : Colors.black38,
            ),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: Stack(
                children: [
                  Container(height: 6,
                        color: isDark
                          ? Colors.white10
                          : Colors.black.withValues(alpha: 0.08)),
                  FractionallySizedBox(
                    widthFactor: hasUsableSample ? score.clamp(0.0, 1.0) : 0.0,
                    child: Container(
                      height: 6,
                      color: color,
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(width: 8),
          Text(
            hasUsableSample ? '${(score * 100).toStringAsFixed(0)}%' : '—',
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w700,
              color: color,
            ),
          ),
        ],
      ),
    );
  }

  // ================================================================
  // INLINE CHAT INPUT — sends text for sentiment analysis
  // ================================================================

  Widget _buildChatInput(bool isDark) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 4, 12, 12),
      child: Row(
        children: [
          Expanded(
            child: TextField(
              controller: _chatController,
              style: TextStyle(
                fontSize: 13,
                color: isDark ? Colors.white70 : Colors.black87,
              ),
              decoration: InputDecoration(
                hintText: 'Type a message to analyze sentiment...',
                hintStyle: TextStyle(
                    fontSize: 12,
                    color: isDark ? Colors.white30 : Colors.black38),
                filled: true,
                fillColor: isDark ? const Color(0xFF252540) : Colors.white,
                contentPadding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(20),
                  borderSide: BorderSide.none,
                ),
                enabledBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(20),
                  borderSide: BorderSide(
                      color: isDark ? Colors.white12 : Colors.black12),
                ),
              ),
            ),
          ),
          const SizedBox(width: 8),
          GestureDetector(
            onTap: () async {
              final text = _chatController.text.trim();
              if (text.isEmpty) return;
              _chatController.clear();
              await widget.onTextSend?.call(text);
            },
            child: Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                color: Colors.teal,
                borderRadius: BorderRadius.circular(18),
              ),
              child: const Icon(Icons.send, color: Colors.white, size: 18),
            ),
          ),
        ],
      ),
    );
  }

  // ================================================================
  // DETAIL VIEW — full screen chart for one channel
  // Navigated to when user taps a bar
  // ================================================================

  void _openDetailView(
      String channel, List<_SentimentPoint> history, IconData icon) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => _SentimentDetailScreen(
          channel:  channel,
          icon:     icon,
          history:  List.from(history),
          callId:   widget.callId,
          currentScore: _score(channel),
          currentLabel: _label(channel),
          currentNotes: _notes(channel),
        ),
      ),
    );
  }

  // ================================================================
  // COLOR HELPER
  // ================================================================

  static Color _scoreColor(double score) {
    if (score >= 0.55) return const Color(0xFF2ECC71);  // green — calm/positive
    if (score >= 0.35) return const Color(0xFFF39C12);  // amber — neutral/anxious
    return const Color(0xFFE74C3C);                      // red — distressed
  }

  static Color _statusColor(String status, double score) {
    switch (status.toUpperCase()) {
      case 'AWAITING':
        return const Color(0xFF95A5A6);
      case 'DEGRADED':
        return const Color(0xFFF39C12);
      case 'COMPLETED':
      default:
        return _scoreColor(score);
    }
  }
}

// ================================================================
// DETAIL SCREEN — line graph history for a single channel
// ================================================================

class _SentimentDetailScreen extends StatelessWidget {
  final String channel;
  final IconData icon;
  final List<_SentimentPoint> history;
  final String callId;
  final double currentScore;
  final String currentLabel;
  final String currentNotes;

  const _SentimentDetailScreen({
    required this.channel,
    required this.icon,
    required this.history,
    required this.callId,
    required this.currentScore,
    required this.currentLabel,
    required this.currentNotes,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final color = _channelColor(channel);

    return Scaffold(
      backgroundColor: isDark ? const Color(0xFF0D0D1A) : const Color(0xFFF5F7FA),
      appBar: AppBar(
        backgroundColor:
            isDark ? const Color(0xFF1A1A2E) : Colors.white,
        foregroundColor: isDark ? Colors.white : Colors.black87,
        title: Row(
          children: [
            Icon(icon, size: 18, color: color),
            const SizedBox(width: 8),
            Text('$channel Sentiment',
                style: const TextStyle(fontWeight: FontWeight.w600)),
          ],
        ),
        elevation: 0,
      ),
      body: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Current snapshot card
            _buildSnapshotCard(isDark, color),
            const SizedBox(height: 24),

            // History chart header
            Text(
              'Score History',
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                letterSpacing: 0.5,
                color: isDark ? Colors.white60 : Colors.black54,
              ),
            ),
            const SizedBox(height: 12),

            // Line chart
            Expanded(
              child: history.isEmpty
                  ? _buildNoData(isDark)
                  : _SentimentLineChart(
                      history: history,
                      color: color,
                      isDark: isDark,
                    ),
            ),

            // Legend
            const SizedBox(height: 16),
            _buildLegend(isDark),
          ],
        ),
      ),
    );
  }

  Widget _buildSnapshotCard(bool isDark, Color color) {
    final cardBg = isDark ? const Color(0xFF1A1A2E) : Colors.white;
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: cardBg,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color.withValues(alpha: 0.3)),
      ),
      child: Row(
        children: [
          // Score ring
          SizedBox(
            width: 64,
            height: 64,
            child: Stack(
              alignment: Alignment.center,
              children: [
                CircularProgressIndicator(
                  value: currentScore,
                  strokeWidth: 6,
                  backgroundColor:
                      isDark ? Colors.white12 : Colors.black12,
                  valueColor: AlwaysStoppedAnimation<Color>(color),
                ),
                Text(
                  '${(currentScore * 100).toStringAsFixed(0)}%',
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w700,
                    color: color,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  currentLabel,
                  style: TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.w700,
                    color: color,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  currentNotes,
                  style: TextStyle(
                    fontSize: 12,
                    fontStyle: FontStyle.italic,
                    color: isDark ? Colors.white54 : Colors.black54,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildNoData(bool isDark) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.hourglass_empty,
              size: 40,
              color: isDark ? Colors.white30 : Colors.black26),
          const SizedBox(height: 12),
          Text(
            'No history yet.\nData points appear every 15 seconds.',
            textAlign: TextAlign.center,
            style: TextStyle(
              color: isDark ? Colors.white38 : Colors.black38,
              fontSize: 13,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildLegend(bool isDark) {
    final items = [
      (_channelColor('POSITIVE_COLOR'), 'Calm / Positive (>55%)'),
      (const Color(0xFFF39C12), 'Neutral / Anxious (35–55%)'),
      (const Color(0xFFE74C3C), 'Distressed / Negative (<35%)'),
    ];

    return Wrap(
      spacing: 16,
      runSpacing: 6,
      children: items.map((item) {
        return Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 10,
              height: 10,
              decoration: BoxDecoration(
                color: item.$1,
                shape: BoxShape.circle,
              ),
            ),
            const SizedBox(width: 4),
            Text(
              item.$2,
              style: TextStyle(
                fontSize: 10,
                color: isDark ? Colors.white54 : Colors.black54,
              ),
            ),
          ],
        );
      }).toList(),
    );
  }

  static Color _channelColor(String channel) {
    switch (channel) {
      case 'TEXT':  return const Color(0xFF3498DB);
      case 'VOICE': return const Color(0xFF9B59B6);
      case 'VIDEO': return const Color(0xFF1ABC9C);
      case 'POSITIVE_COLOR': return const Color(0xFF2ECC71);
      default: return const Color(0xFF3498DB);
    }
  }
}

// ================================================================
// LINE CHART — custom painter for sentiment history
// ================================================================

class _SentimentLineChart extends StatelessWidget {
  final List<_SentimentPoint> history;
  final Color color;
  final bool isDark;

  const _SentimentLineChart({
    required this.history,
    required this.color,
    required this.isDark,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF1A1A2E) : Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isDark ? Colors.white10 : Colors.black.withValues(alpha: 0.08),
        ),
      ),
      padding: const EdgeInsets.all(16),
      child: CustomPaint(
        painter: _LineChartPainter(
          points: history,
          lineColor: color,
          gridColor:
              isDark ? Colors.white12 : Colors.black.withValues(alpha: 0.08),
          labelColor:
              isDark ? Colors.white38 : Colors.black38,
        ),
        child: const SizedBox.expand(),
      ),
    );
  }
}

class _LineChartPainter extends CustomPainter {
  final List<_SentimentPoint> points;
  final Color lineColor;
  final Color gridColor;
  final Color labelColor;

  _LineChartPainter({
    required this.points,
    required this.lineColor,
    required this.gridColor,
    required this.labelColor,
  });

  @override
  void paint(Canvas canvas, Size size) {
    if (points.isEmpty) return;

    const paddingLeft   = 32.0;
    const paddingBottom = 24.0;
    const paddingTop    = 8.0;
    const paddingRight  = 8.0;

    final chartW = size.width  - paddingLeft - paddingRight;
    final chartH = size.height - paddingTop  - paddingBottom;

    // Grid lines at 0, 25, 50, 75, 100%
    final gridPaint = Paint()
      ..color = gridColor
      ..strokeWidth = 0.5;
    final labelStyle = TextStyle(color: labelColor, fontSize: 9);

    for (final pct in [0, 25, 50, 75, 100]) {
      final y = paddingTop + chartH * (1 - pct / 100);
      canvas.drawLine(
        Offset(paddingLeft, y),
        Offset(paddingLeft + chartW, y),
        gridPaint,
      );
      final tp = TextPainter(
        text: TextSpan(text: '$pct', style: labelStyle),
        textDirection: TextDirection.ltr,
      )..layout();
      tp.paint(canvas, Offset(paddingLeft - tp.width - 4, y - tp.height / 2));
    }

    // Color zone fills
    void fillZone(double y0, double y1, Color c) {
      canvas.drawRect(
        Rect.fromLTRB(paddingLeft, paddingTop + chartH * y0,
            paddingLeft + chartW, paddingTop + chartH * y1),
        Paint()..color = c.withValues(alpha: 0.04),
      );
    }
    fillZone(0.0,   0.45, const Color(0xFF2ECC71));
    fillZone(0.45,  0.65, const Color(0xFFF39C12));
    fillZone(0.65,  1.0,  const Color(0xFFE74C3C));

    // Data line
    final linePaint = Paint()
      ..color = lineColor
      ..strokeWidth = 2
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;

    final path = Path();
    for (var i = 0; i < points.length; i++) {
      final x = paddingLeft + chartW * i / math.max(points.length - 1, 1);
      final y = paddingTop  + chartH * (1 - points[i].score.clamp(0.0, 1.0));
      i == 0 ? path.moveTo(x, y) : path.lineTo(x, y);
    }
    canvas.drawPath(path, linePaint);

    // Data points
    final dotPaint  = Paint()..color = lineColor;
    final dotBorder = Paint()
      ..color = Colors.white
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.5;

    for (var i = 0; i < points.length; i++) {
      final x = paddingLeft + chartW * i / math.max(points.length - 1, 1);
      final y = paddingTop  + chartH * (1 - points[i].score.clamp(0.0, 1.0));
      canvas.drawCircle(Offset(x, y), 3.5, dotPaint);
      canvas.drawCircle(Offset(x, y), 3.5, dotBorder);
    }
  }

  @override
  bool shouldRepaint(_LineChartPainter old) => old.points != points;
}

// ================================================================
// DATA CLASS
// ================================================================

class _SentimentPoint {
  final DateTime time;
  final double score;
  final String label;

  const _SentimentPoint(this.time, this.score, this.label);
}
