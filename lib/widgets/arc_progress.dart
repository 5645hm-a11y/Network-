import 'dart:math';
import 'package:flutter/material.dart';
import '../theme/app_theme.dart';

class ArcProgress extends StatefulWidget {
  final double progress; // 0.0 – 1.0
  final double size;
  final Color color;
  final Widget? child;

  const ArcProgress({
    super.key,
    required this.progress,
    this.size = 200,
    this.color = mint,
    this.child,
  });

  @override
  State<ArcProgress> createState() => _ArcProgressState();
}

class _ArcProgressState extends State<ArcProgress>
    with SingleTickerProviderStateMixin {
  late AnimationController _spin;

  @override
  void initState() {
    super.initState();
    _spin = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 2),
    )..repeat();
  }

  @override
  void dispose() {
    _spin.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _spin,
      builder: (_, __) => CustomPaint(
        size: Size(widget.size, widget.size),
        painter: _ArcPainter(
          progress: widget.progress,
          color: widget.color,
          spinAngle: _spin.value * 2 * pi,
        ),
        child: SizedBox(
          width: widget.size,
          height: widget.size,
          child: Center(child: widget.child),
        ),
      ),
    );
  }
}

class _ArcPainter extends CustomPainter {
  final double progress;
  final Color color;
  final double spinAngle;

  _ArcPainter({
    required this.progress,
    required this.color,
    required this.spinAngle,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = size.width / 2 - 10;

    // Track
    canvas.drawCircle(
      center,
      radius,
      Paint()
        ..color = bgSurface
        ..style = PaintingStyle.stroke
        ..strokeWidth = 8,
    );

    // Progress arc
    final sweepAngle = 2 * pi * progress;
    canvas.drawArc(
      Rect.fromCircle(center: center, radius: radius),
      -pi / 2,
      sweepAngle,
      false,
      Paint()
        ..color = color
        ..style = PaintingStyle.stroke
        ..strokeWidth = 8
        ..strokeCap = StrokeCap.round,
    );

    // Glow
    canvas.drawArc(
      Rect.fromCircle(center: center, radius: radius),
      -pi / 2,
      sweepAngle,
      false,
      Paint()
        ..color = color.withAlpha(60)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 16
        ..strokeCap = StrokeCap.round,
    );

    // Spinning dot
    if (progress > 0) {
      final dotAngle = -pi / 2 + sweepAngle + spinAngle * 0.3;
      final dotX = center.dx + radius * cos(dotAngle);
      final dotY = center.dy + radius * sin(dotAngle);
      canvas.drawCircle(
        Offset(dotX, dotY),
        6,
        Paint()..color = color,
      );
      canvas.drawCircle(
        Offset(dotX, dotY),
        10,
        Paint()..color = color.withAlpha(80),
      );
    }
  }

  @override
  bool shouldRepaint(_ArcPainter old) =>
      old.progress != progress || old.spinAngle != spinAngle;
}
