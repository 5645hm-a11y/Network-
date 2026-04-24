import 'dart:math';
import 'package:flutter/material.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:provider/provider.dart';
import '../models/vpn_state.dart';
import '../providers/vpn_provider.dart';
import '../theme/app_theme.dart';
import '../widgets/ui_kit.dart';

class DashboardScreen extends StatefulWidget {
  final VoidCallback onTraffic;
  final VoidCallback onCache;
  final VoidCallback onSettings;

  const DashboardScreen({
    super.key,
    required this.onTraffic,
    required this.onCache,
    required this.onSettings,
  });

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  int _quotaMb = 500;

  @override
  Widget build(BuildContext context) {
    final l  = AppLocalizations.of(context)!;
    final vp = context.watch<VpnProvider>();
    final s  = vp.state;

    return Container(
      decoration: BoxDecoration(gradient: bgGradient),
      child: Stack(
        children: [
          // Ambient orbs
          _Orbs(running: s.running, mode: s.mode),

          SafeArea(
            child: Column(
              children: [
                _TopBar(
                  state: s,
                  onTraffic: widget.onTraffic,
                  onCache: widget.onCache,
                  onSettings: widget.onSettings,
                  l: l,
                ),
                Expanded(
                  child: SingleChildScrollView(
                    physics: const BouncingScrollPhysics(),
                    padding: const EdgeInsets.fromLTRB(16, 4, 16, 24),
                    child: Column(
                      children: [
                        _AgentPanel(state: s),
                        const SizedBox(height: 14),
                        AnimatedSwitcher(
                          duration: const Duration(milliseconds: 500),
                          switchInCurve: Curves.easeOutCubic,
                          switchOutCurve: Curves.easeInCubic,
                          child: !s.running
                              ? _IdleCard(
                                  key: const ValueKey('idle'),
                                  quotaMb: _quotaMb,
                                  onQuotaChanged: (v) => setState(() => _quotaMb = v),
                                  onAbsorb: () => vp.startAbsorb(_quotaMb),
                                  onServe: () => vp.startServe(),
                                  l: l,
                                )
                              : s.mode == VpnMode.absorb
                                  ? _AbsorbCard(
                                      key: const ValueKey('absorb'),
                                      state: s,
                                      onStop: () => vp.stop(),
                                      l: l,
                                    )
                                  : _ServeCard(
                                      key: const ValueKey('serve'),
                                      state: s,
                                      onStop: () => vp.stop(),
                                      l: l,
                                    ),
                        ),
                        if (s.running && s.mode == VpnMode.absorb && s.topDomains.isNotEmpty) ...[
                          const SizedBox(height: 14),
                          _DomainFeed(domains: s.topDomains),
                        ],
                        const SizedBox(height: 14),
                        _BottomStats(state: s, l: l),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

// ── Ambient orbs ──────────────────────────────────────────────────────────────
class _Orbs extends StatelessWidget {
  final bool running;
  final VpnMode mode;
  const _Orbs({required this.running, required this.mode});

  @override
  Widget build(BuildContext context) {
    final c1 = running
        ? (mode == VpnMode.absorb ? cMint : cCyan)
        : cElectric;
    return CustomPaint(
      painter: _OrbPainter(c1: c1, c2: cViolet),
      child: const SizedBox.expand(),
    );
  }
}

class _OrbPainter extends CustomPainter {
  final Color c1, c2;
  _OrbPainter({required this.c1, required this.c2});

  @override
  void paint(Canvas canvas, Size sz) {
    canvas.drawCircle(
      Offset(sz.width * 0.85, sz.height * 0.08),
      sz.width * 0.45,
      Paint()
        ..shader = RadialGradient(colors: [c1.withAlpha(25), Colors.transparent])
            .createShader(Rect.fromCircle(
                center: Offset(sz.width * 0.85, sz.height * 0.08),
                radius: sz.width * 0.45)),
    );
    canvas.drawCircle(
      Offset(sz.width * 0.1, sz.height * 0.55),
      sz.width * 0.35,
      Paint()
        ..shader = RadialGradient(colors: [c2.withAlpha(20), Colors.transparent])
            .createShader(Rect.fromCircle(
                center: Offset(sz.width * 0.1, sz.height * 0.55),
                radius: sz.width * 0.35)),
    );
  }

  @override
  bool shouldRepaint(_OrbPainter o) => o.c1 != c1 || o.c2 != c2;
}

// ── Top Bar ───────────────────────────────────────────────────────────────────
class _TopBar extends StatelessWidget {
  final VpnState state;
  final VoidCallback onTraffic, onCache, onSettings;
  final AppLocalizations l;

  const _TopBar({
    required this.state,
    required this.onTraffic,
    required this.onCache,
    required this.onSettings,
    required this.l,
  });

  @override
  Widget build(BuildContext context) {
    Color dot;
    if (!state.running) dot = cTextSec;
    else if (state.mode == VpnMode.absorb) dot = cMint;
    else dot = cCyan;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      child: Row(
        children: [
          PulsingDot(color: dot, size: 8),
          const SizedBox(width: 10),
          const Text(
            'Virtual SIM',
            style: TextStyle(
                color: cTextPrim, fontSize: 17, fontWeight: FontWeight.w700,
                letterSpacing: 0.5),
          ),
          const Spacer(),
          _NavBtn(icon: Icons.show_chart_rounded, onTap: onTraffic),
          _NavBtn(icon: Icons.storage_rounded, onTap: onCache),
          _NavBtn(icon: Icons.tune_rounded, onTap: onSettings),
        ],
      ),
    );
  }
}

class _NavBtn extends StatelessWidget {
  final IconData icon;
  final VoidCallback onTap;
  const _NavBtn({required this.icon, required this.onTap});

  @override
  Widget build(BuildContext context) => GestureDetector(
        onTap: onTap,
        behavior: HitTestBehavior.opaque,
        child: Padding(
          padding: const EdgeInsets.all(8),
          child: Icon(icon, color: cTextSec, size: 22),
        ),
      );
}

// ── Agent Panel ───────────────────────────────────────────────────────────────
class _AgentPanel extends StatelessWidget {
  final VpnState state;
  const _AgentPanel({required this.state});

  @override
  Widget build(BuildContext context) {
    return GlassPanel(
      glowColor: cViolet,
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      child: Row(
        children: [
          state.agentThinking
              ? SizedBox(
                  width: 16, height: 16,
                  child: CircularProgressIndicator(
                      color: cViolet, strokeWidth: 1.5))
              : const Icon(Icons.psychology_rounded, color: cViolet, size: 16),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              state.agentMessage.isEmpty ? 'Virtual SIM Agent' : state.agentMessage,
              style: const TextStyle(color: cTextPrim, fontSize: 12.5),
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          if (!state.agentHasKey) ...[
            const SizedBox(width: 8),
            _Badge('API Key', cAmber),
          ],
        ],
      ),
    );
  }
}

class _Badge extends StatelessWidget {
  final String label;
  final Color color;
  const _Badge(this.label, this.color);

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
        decoration: BoxDecoration(
          color: color.withAlpha(25),
          borderRadius: BorderRadius.circular(6),
          border: Border.all(color: color.withAlpha(80)),
        ),
        child: Text(label,
            style: TextStyle(color: color, fontSize: 10, fontWeight: FontWeight.w600)),
      );
}

// ── Idle Card ─────────────────────────────────────────────────────────────────
class _IdleCard extends StatelessWidget {
  final int quotaMb;
  final ValueChanged<int> onQuotaChanged;
  final VoidCallback onAbsorb, onServe;
  final AppLocalizations l;

  const _IdleCard({
    super.key,
    required this.quotaMb,
    required this.onQuotaChanged,
    required this.onAbsorb,
    required this.onServe,
    required this.l,
  });

  @override
  Widget build(BuildContext context) {
    return GlassPanel(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Step flow
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              _Step(Icons.wifi_rounded, l.step1, cElectric),
              _Arrow(),
              _Step(Icons.download_rounded, l.step2, cMint),
              _Arrow(),
              _Step(Icons.flight_rounded, l.step3, cAmber),
              _Arrow(),
              _Step(Icons.language_rounded, l.step4, cCyan),
            ],
          ),
          const SizedBox(height: 20),

          // Quota row
          Row(
            children: [
              Text(l.quota,
                  style: const TextStyle(color: cTextMid, fontSize: 13)),
              const Spacer(),
              GlowText('$quotaMb MB', color: cMint, size: 15, weight: FontWeight.w700),
            ],
          ),
          const SizedBox(height: 6),
          _QuotaSlider(value: quotaMb, onChanged: onQuotaChanged),
          const SizedBox(height: 18),

          NeonButton(
            label: l.startAbsorbing,
            icon: Icons.download_rounded,
            color: cMint,
            onTap: onAbsorb,
          ),
          const SizedBox(height: 10),
          NeonButton(
            label: l.startVirtualSim,
            icon: Icons.sim_card_rounded,
            color: cCyan,
            onTap: onServe,
            outlined: true,
          ),
        ],
      ),
    );
  }
}

class _Step extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color color;
  const _Step(this.icon, this.label, this.color);

  @override
  Widget build(BuildContext context) => Column(
        children: [
          Container(
            width: 40, height: 40,
            decoration: BoxDecoration(
              color: color.withAlpha(20),
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: color.withAlpha(60)),
            ),
            child: Icon(icon, color: color, size: 18),
          ),
          const SizedBox(height: 4),
          Text(label,
              style: const TextStyle(color: cTextSec, fontSize: 9),
              textAlign: TextAlign.center),
        ],
      );
}

class _Arrow extends StatelessWidget {
  @override
  Widget build(BuildContext context) =>
      const Icon(Icons.chevron_right_rounded, color: cBorder, size: 16);
}

class _QuotaSlider extends StatelessWidget {
  final int value;
  final ValueChanged<int> onChanged;
  const _QuotaSlider({required this.value, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    return SliderTheme(
      data: SliderThemeData(
        trackHeight: 3,
        activeTrackColor: cMint,
        inactiveTrackColor: cBorder,
        thumbColor: cMint,
        overlayColor: cMint.withAlpha(30),
        thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 7),
        overlayShape: const RoundSliderOverlayShape(overlayRadius: 16),
      ),
      child: Slider(
        value: value.toDouble(),
        min: 100, max: 2048, divisions: 19,
        onChanged: (v) => onChanged(v.toInt()),
      ),
    );
  }
}

// ── Absorbing Card ────────────────────────────────────────────────────────────
class _AbsorbCard extends StatelessWidget {
  final VpnState state;
  final VoidCallback onStop;
  final AppLocalizations l;
  const _AbsorbCard({super.key, required this.state, required this.onStop, required this.l});

  @override
  Widget build(BuildContext context) {
    final pct = state.quotaMb > 0
        ? (state.progressMb / state.quotaMb).clamp(0.0, 1.0)
        : 0.0;

    return GlassPanel(
      glowColor: cMint,
      child: Column(
        children: [
          ArcProgress(
            progress: pct,
            size: 180,
            color: cMint,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                GlowText('${(pct * 100).toInt()}%', color: cMint, size: 34),
                const SizedBox(height: 2),
                Text(l.absorbing,
                    style: const TextStyle(color: cTextSec, fontSize: 11)),
              ],
            ),
          ),
          const SizedBox(height: 16),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              _Stat(_fmt(state.progressMb), l.absorbed, cMint, Icons.download_rounded),
              _Divider(),
              _Stat('${state.sessionRequests}', l.urls, cElectric, Icons.link_rounded),
            ],
          ),
          const SizedBox(height: 18),
          NeonButton(
            label: l.stop, icon: Icons.stop_rounded,
            color: cRed, onTap: onStop, outlined: true,
          ),
        ],
      ),
    );
  }
}

// ── Serve Card ────────────────────────────────────────────────────────────────
class _ServeCard extends StatelessWidget {
  final VpnState state;
  final VoidCallback onStop;
  final AppLocalizations l;
  const _ServeCard({super.key, required this.state, required this.onStop, required this.l});

  @override
  Widget build(BuildContext context) {
    return GlassPanel(
      glowColor: cCyan,
      child: Column(
        children: [
          const SizedBox(height: 8),
          Container(
            width: 76, height: 76,
            decoration: BoxDecoration(
              color: cCyan.withAlpha(20),
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: cCyan.withAlpha(80), width: 1.5),
              boxShadow: [BoxShadow(color: cCyan.withAlpha(50), blurRadius: 24)],
            ),
            child: const Icon(Icons.sim_card_rounded, color: cCyan, size: 38),
          ),
          const SizedBox(height: 12),
          GlowText(l.simActive, color: cCyan, size: 20),
          const SizedBox(height: 4),
          Text(
            '${_fmt(state.cacheSizeMb)} · ${state.cacheEntryCount} pages',
            style: const TextStyle(color: cTextSec, fontSize: 12),
          ),
          const SizedBox(height: 18),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              _Stat(_fmt(state.cacheSizeMb), l.cached, cCyan, Icons.storage_rounded),
              _Divider(),
              _Stat('${state.cacheHits}', l.cacheHits, cMint, Icons.offline_bolt_rounded),
            ],
          ),
          const SizedBox(height: 18),
          NeonButton(
            label: l.stop, icon: Icons.stop_rounded,
            color: cRed, onTap: onStop, outlined: true,
          ),
          const SizedBox(height: 8),
        ],
      ),
    );
  }
}

class _Stat extends StatelessWidget {
  final String value, label;
  final Color color;
  final IconData icon;
  const _Stat(this.value, this.label, this.color, this.icon);

  @override
  Widget build(BuildContext context) => Column(
        children: [
          Icon(icon, color: color, size: 18),
          const SizedBox(height: 4),
          GlowText(value, color: color, size: 18, weight: FontWeight.w700),
          Text(label, style: const TextStyle(color: cTextSec, fontSize: 10)),
        ],
      );
}

class _Divider extends StatelessWidget {
  @override
  Widget build(BuildContext context) =>
      Container(width: 1, height: 40, color: cBorder);
}

// ── Domain Feed ───────────────────────────────────────────────────────────────
class _DomainFeed extends StatelessWidget {
  final List<MapEntry<String, int>> domains;
  const _DomainFeed({required this.domains});

  @override
  Widget build(BuildContext context) {
    return GlassPanel(
      padding: const EdgeInsets.all(14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            PulsingDot(color: cMint, size: 7),
            const SizedBox(width: 8),
            const Text('LIVE FEED',
                style: TextStyle(color: cTextSec, fontSize: 10, letterSpacing: 1.8,
                    fontWeight: FontWeight.w700)),
          ]),
          const SizedBox(height: 10),
          ...domains.take(5).map((e) => Padding(
                padding: const EdgeInsets.symmetric(vertical: 3),
                child: Row(children: [
                  Container(
                    width: 5, height: 5, margin: const EdgeInsets.only(right: 8),
                    decoration: const BoxDecoration(color: cMint, shape: BoxShape.circle),
                  ),
                  Expanded(
                    child: Text(
                      Uri.tryParse(e.key)?.host ?? e.key,
                      style: const TextStyle(color: cTextPrim, fontSize: 11.5),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  Text('${e.value}',
                      style: const TextStyle(color: cTextSec, fontSize: 10)),
                ]),
              )),
        ],
      ),
    );
  }
}

// ── Bottom Stats ──────────────────────────────────────────────────────────────
class _BottomStats extends StatelessWidget {
  final VpnState state;
  final AppLocalizations l;
  const _BottomStats({required this.state, required this.l});

  @override
  Widget build(BuildContext context) {
    return Row(children: [
      Expanded(
        child: GlassPanel(
          padding: const EdgeInsets.all(14),
          child: Row(children: [
            const Icon(Icons.link_rounded, color: cElectric, size: 18),
            const SizedBox(width: 10),
            Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              GlowText('${state.cacheEntryCount}', color: cElectric, size: 20),
              Text(l.urls, style: const TextStyle(color: cTextSec, fontSize: 10)),
            ]),
          ]),
        ),
      ),
      const SizedBox(width: 12),
      Expanded(
        child: GlassPanel(
          padding: const EdgeInsets.all(14),
          child: Row(children: [
            const Icon(Icons.storage_rounded, color: cMint, size: 18),
            const SizedBox(width: 10),
            Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              GlowText(_fmt(state.cacheSizeMb), color: cMint, size: 20),
              Text(l.mb, style: const TextStyle(color: cTextSec, fontSize: 10)),
            ]),
          ]),
        ),
      ),
    ]);
  }
}

String _fmt(double mb) {
  if (mb >= 1024) return '${(mb / 1024).toStringAsFixed(1)}G';
  if (mb >= 1)    return '${mb.toStringAsFixed(0)}M';
  return '${(mb * 1024).toStringAsFixed(0)}K';
}
