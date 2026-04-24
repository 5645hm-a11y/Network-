import 'dart:math';
import 'package:flutter/material.dart';
import '../theme/app_theme.dart';

// ── Glass Panel ───────────────────────────────────────────────────────────────
class GlassPanel extends StatelessWidget {
  final Widget child;
  final Color? glowColor;
  final EdgeInsets padding;
  final double radius;

  const GlassPanel({
    super.key,
    required this.child,
    this.glowColor,
    this.padding = const EdgeInsets.all(16),
    this.radius = 18,
  });

  @override
  Widget build(BuildContext context) {
    final gc = glowColor;
    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(radius),
        color: cSurface,
        border: Border.all(
          color: gc != null ? gc.withAlpha(80) : cBorder,
          width: gc != null ? 1.5 : 1,
        ),
        boxShadow: gc != null
            ? [BoxShadow(color: gc.withAlpha(30), blurRadius: 24, spreadRadius: 2)]
            : null,
      ),
      child: Padding(padding: padding, child: child),
    );
  }
}

// ── Neon Button ───────────────────────────────────────────────────────────────
class NeonButton extends StatefulWidget {
  final String label;
  final IconData? icon;
  final Color color;
  final VoidCallback onTap;
  final bool outlined;
  final double height;

  const NeonButton({
    super.key,
    required this.label,
    required this.color,
    required this.onTap,
    this.icon,
    this.outlined = false,
    this.height = 52,
  });

  @override
  State<NeonButton> createState() => _NeonButtonState();
}

class _NeonButtonState extends State<NeonButton>
    with SingleTickerProviderStateMixin {
  late AnimationController _ctrl;
  late Animation<double> _scale;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(vsync: this, duration: const Duration(milliseconds: 120));
    _scale = Tween(begin: 1.0, end: 0.96).animate(
        CurvedAnimation(parent: _ctrl, curve: Curves.easeOut));
  }

  @override
  void dispose() { _ctrl.dispose(); super.dispose(); }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapDown: (_) => _ctrl.forward(),
      onTapUp: (_) { _ctrl.reverse(); widget.onTap(); },
      onTapCancel: () => _ctrl.reverse(),
      child: ScaleTransition(
        scale: _scale,
        child: Container(
          height: widget.height,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(14),
            gradient: widget.outlined
                ? null
                : LinearGradient(
                    colors: [widget.color, widget.color.withAlpha(180)],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
            border: Border.all(color: widget.color, width: 1.5),
            boxShadow: [
              BoxShadow(
                color: widget.color.withAlpha(widget.outlined ? 40 : 80),
                blurRadius: 12,
                spreadRadius: widget.outlined ? 0 : 1,
              ),
            ],
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              if (widget.icon != null) ...[
                Icon(widget.icon,
                    color: widget.outlined ? widget.color : cBg,
                    size: 18),
                const SizedBox(width: 8),
              ],
              Text(
                widget.label,
                style: TextStyle(
                  color: widget.outlined ? widget.color : cBg,
                  fontWeight: FontWeight.w700,
                  fontSize: 15,
                  letterSpacing: 0.3,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// ── Pulsing Dot ───────────────────────────────────────────────────────────────
class PulsingDot extends StatefulWidget {
  final Color color;
  final double size;

  const PulsingDot({super.key, required this.color, this.size = 8});

  @override
  State<PulsingDot> createState() => _PulsingDotState();
}

class _PulsingDotState extends State<PulsingDot>
    with SingleTickerProviderStateMixin {
  late AnimationController _ctrl;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
        vsync: this, duration: const Duration(milliseconds: 1200))
      ..repeat(reverse: true);
  }

  @override
  void dispose() { _ctrl.dispose(); super.dispose(); }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _ctrl,
      builder: (_, __) => Container(
        width: widget.size,
        height: widget.size,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          color: widget.color,
          boxShadow: [
            BoxShadow(
              color: widget.color.withAlpha((150 * _ctrl.value).toInt()),
              blurRadius: 6 + 6 * _ctrl.value,
              spreadRadius: 1 * _ctrl.value,
            ),
          ],
        ),
      ),
    );
  }
}

// ── Section Label ─────────────────────────────────────────────────────────────
class SectionLabel extends StatelessWidget {
  final String text;
  final Color color;
  const SectionLabel(this.text, {super.key, this.color = cTextSec});

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 10, left: 2),
        child: Text(
          text.toUpperCase(),
          style: TextStyle(
              color: color,
              fontSize: 10,
              fontWeight: FontWeight.w700,
              letterSpacing: 2),
        ),
      );
}

// ── Glow Text ─────────────────────────────────────────────────────────────────
class GlowText extends StatelessWidget {
  final String text;
  final Color color;
  final double size;
  final FontWeight weight;

  const GlowText(
    this.text, {
    super.key,
    required this.color,
    this.size = 32,
    this.weight = FontWeight.w800,
  });

  @override
  Widget build(BuildContext context) {
    return Text(
      text,
      style: TextStyle(
        color: color,
        fontSize: size,
        fontWeight: weight,
        shadows: [
          Shadow(color: color.withAlpha(180), blurRadius: 16),
          Shadow(color: color.withAlpha(80), blurRadius: 32),
        ],
      ),
    );
  }
}

// ── Arc Progress (custom painter) ─────────────────────────────────────────────
class ArcProgress extends StatefulWidget {
  final double progress; // 0.0 – 1.0
  final double size;
  final Color color;
  final Widget? child;

  const ArcProgress({
    super.key,
    required this.progress,
    this.size = 200,
    this.color = cMint,
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
        vsync: this, duration: const Duration(seconds: 3))
      ..repeat();
  }

  @override
  void dispose() { _spin.dispose(); super.dispose(); }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _spin,
      builder: (_, __) => CustomPaint(
        size: Size(widget.size, widget.size),
        painter: _ArcPainter(
          progress: widget.progress,
          color: widget.color,
          spin: _spin.value * 2 * pi,
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
  final double spin;

  _ArcPainter({required this.progress, required this.color, required this.spin});

  @override
  void paint(Canvas canvas, Size size) {
    final c = Offset(size.width / 2, size.height / 2);
    final r = size.width / 2 - 14;

    // Outer glow ring
    canvas.drawCircle(c, r + 6,
        Paint()..color = color.withAlpha(12)..style = PaintingStyle.stroke..strokeWidth = 16);

    // Track
    canvas.drawCircle(c, r,
        Paint()..color = const Color(0xFF111D2E)..style = PaintingStyle.stroke..strokeWidth = 6);

    if (progress <= 0) return;
    final sweep = 2 * pi * progress;

    // Glow arc
    canvas.drawArc(
      Rect.fromCircle(center: c, radius: r),
      -pi / 2, sweep, false,
      Paint()
        ..color = color.withAlpha(50)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 14
        ..strokeCap = StrokeCap.round,
    );

    // Main arc
    canvas.drawArc(
      Rect.fromCircle(center: c, radius: r),
      -pi / 2, sweep, false,
      Paint()
        ..color = color
        ..style = PaintingStyle.stroke
        ..strokeWidth = 5
        ..strokeCap = StrokeCap.round,
    );

    // Spinning dot
    final dotAngle = -pi / 2 + sweep;
    final dx = c.dx + r * cos(dotAngle);
    final dy = c.dy + r * sin(dotAngle);
    canvas.drawCircle(Offset(dx, dy), 7,
        Paint()..color = color.withAlpha(60));
    canvas.drawCircle(Offset(dx, dy), 4,
        Paint()..color = color);
  }

  @override
  bool shouldRepaint(_ArcPainter o) =>
      o.progress != progress || o.spin != spin;
}
