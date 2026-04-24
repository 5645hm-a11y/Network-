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

    return Material(
      color: Colors.transparent,
      child: Container(
        decoration: BoxDecoration(gradient: bgGradient),
        child: Stack(
          children: [
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
                      padding: const EdgeInsets.fromLTRB(16, 0, 16, 32),
                      child: Column(
                        children: [
                          _AgentPanel(state: s),
                          const SizedBox(height: 16),
                          AnimatedSwitcher(
                            duration: const Duration(milliseconds: 400),
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
                                        onStop: vp.stop,
                                        l: l,
                                      )
                                    : _ServeCard(
                                        key: const ValueKey('serve'),
                                        state: s,
                                        onStop: vp.stop,
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
      ),
    );
  }
}

// ── Ambient Orbs ──────────────────────────────────────────────────────────────
class _Orbs extends StatelessWidget {
  final bool running;
  final VpnMode mode;
  const _Orbs({required this.running, required this.mode});

  @override
  Widget build(BuildContext context) {
    final c1 = running ? (mode == VpnMode.absorb ? cMint : cCyan) : cElectric;
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
      Offset(sz.width * 0.85, sz.height * 0.1),
      sz.width * 0.55,
      Paint()
        ..shader = RadialGradient(colors: [c1.withAlpha(35), Colors.transparent])
            .createShader(Rect.fromCircle(
                center: Offset(sz.width * 0.85, sz.height * 0.1),
                radius: sz.width * 0.55)),
    );
    canvas.drawCircle(
      Offset(sz.width * 0.08, sz.height * 0.6),
      sz.width * 0.4,
      Paint()
        ..shader = RadialGradient(colors: [c2.withAlpha(28), Colors.transparent])
            .createShader(Rect.fromCircle(
                center: Offset(sz.width * 0.08, sz.height * 0.6),
                radius: sz.width * 0.4)),
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
    final Color dot;
    if (!state.running) dot = cTextSec;
    else if (state.mode == VpnMode.absorb) dot = cMint;
    else dot = cCyan;

    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 14, 12, 8),
      child: Row(
        children: [
          PulsingDot(color: dot, size: 9),
          const SizedBox(width: 10),
          const Text(
            'Virtual SIM',
            style: TextStyle(
              color: cTextPrim,
              fontSize: 18,
              fontWeight: FontWeight.w700,
              letterSpacing: 0.3,
            ),
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
          padding: const EdgeInsets.all(10),
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
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
      child: Row(
        children: [
          Container(
            width: 32, height: 32,
            decoration: BoxDecoration(
              color: cViolet.withAlpha(25),
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: cViolet.withAlpha(60)),
            ),
            child: state.agentThinking
                ? Padding(
                    padding: const EdgeInsets.all(8),
                    child: CircularProgressIndicator(color: cViolet, strokeWidth: 1.5),
                  )
                : const Icon(Icons.psychology_rounded, color: cViolet, size: 18),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              state.agentMessage.isEmpty ? 'Virtual SIM Agent' : state.agentMessage,
              style: const TextStyle(color: cTextPrim, fontSize: 13, height: 1.4),
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
        padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
        decoration: BoxDecoration(
          color: color.withAlpha(22),
          borderRadius: BorderRadius.circular(7),
          border: Border.all(color: color.withAlpha(90)),
        ),
        child: Text(label,
            style: TextStyle(
                color: color, fontSize: 10.5, fontWeight: FontWeight.w700,
                letterSpacing: 0.3)),
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
      padding: const EdgeInsets.fromLTRB(20, 22, 20, 22),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // ── Hero centre ──────────────────────────────────────────────────
          Center(child: _IdleHero()),
          const SizedBox(height: 28),

          // ── Step flow ────────────────────────────────────────────────────
          _StepFlow(l: l),
          const SizedBox(height: 26),

          // ── Quota ────────────────────────────────────────────────────────
          Row(
            children: [
              const Icon(Icons.data_usage_rounded, color: cTextSec, size: 15),
              const SizedBox(width: 6),
              Text(l.quota,
                  style: const TextStyle(
                      color: cTextMid, fontSize: 13, fontWeight: FontWeight.w500)),
              const Spacer(),
              GlowText('$quotaMb MB', color: cMint, size: 16, weight: FontWeight.w700),
            ],
          ),
          const SizedBox(height: 8),
          _QuotaSlider(value: quotaMb, onChanged: onQuotaChanged),
          const SizedBox(height: 22),

          // ── Actions ──────────────────────────────────────────────────────
          NeonButton(
            label: l.startAbsorbing,
            icon: Icons.download_rounded,
            color: cMint,
            onTap: onAbsorb,
            height: 54,
          ),
          const SizedBox(height: 11),
          NeonButton(
            label: l.startVirtualSim,
            icon: Icons.sim_card_rounded,
            color: cCyan,
            onTap: onServe,
            outlined: true,
            height: 52,
          ),
        ],
      ),
    );
  }
}

class _IdleHero extends StatefulWidget {
  @override
  State<_IdleHero> createState() => _IdleHeroState();
}

class _IdleHeroState extends State<_IdleHero> with SingleTickerProviderStateMixin {
  late AnimationController _ctrl;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(vsync: this, duration: const Duration(seconds: 3))
      ..repeat(reverse: true);
  }

  @override
  void dispose() { _ctrl.dispose(); super.dispose(); }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _ctrl,
      builder: (_, __) {
        final t = _ctrl.value;
        return Container(
          width: 110, height: 110,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: const Color(0xFF0D1B2E),
            border: Border.all(color: cElectric.withAlpha(80), width: 1.5),
            boxShadow: [
              BoxShadow(
                color: cElectric.withAlpha((30 + 40 * t).toInt()),
                blurRadius: 24 + 20 * t,
                spreadRadius: 2 + 4 * t,
              ),
              BoxShadow(
                color: cViolet.withAlpha((15 + 20 * t).toInt()),
                blurRadius: 40 + 20 * t,
                spreadRadius: 0,
              ),
            ],
          ),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.sim_card_rounded,
                  color: cElectric.withAlpha((180 + 75 * t).toInt()), size: 38),
              const SizedBox(height: 4),
              Text(
                'READY',
                style: TextStyle(
                  color: cElectric.withAlpha((140 + 80 * t).toInt()),
                  fontSize: 10,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 2.5,
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _StepFlow extends StatelessWidget {
  final AppLocalizations l;
  const _StepFlow({required this.l});

  @override
  Widget build(BuildContext context) {
    final steps = [
      (Icons.wifi_rounded,       l.step1, cElectric),
      (Icons.download_rounded,   l.step2, cMint),
      (Icons.flight_rounded,     l.step3, cAmber),
      (Icons.language_rounded,   l.step4, cCyan),
    ];

    return Row(
      children: [
        for (int i = 0; i < steps.length; i++) ...[
          Expanded(child: _StepItem(steps[i].$1, steps[i].$2, steps[i].$3, i + 1)),
          if (i < steps.length - 1)
            Expanded(
              child: Container(
                height: 1.5,
                margin: const EdgeInsets.only(bottom: 18),
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [
                      steps[i].$3.withAlpha(60),
                      steps[i + 1].$3.withAlpha(60),
                    ],
                  ),
                ),
              ),
            ),
        ],
      ],
    );
  }
}

class _StepItem extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color color;
  final int num;
  const _StepItem(this.icon, this.label, this.color, this.num);

  @override
  Widget build(BuildContext context) => Column(
        children: [
          Stack(
            clipBehavior: Clip.none,
            children: [
              Container(
                width: 46, height: 46,
                decoration: BoxDecoration(
                  color: color.withAlpha(18),
                  shape: BoxShape.circle,
                  border: Border.all(color: color.withAlpha(70), width: 1.5),
                  boxShadow: [BoxShadow(color: color.withAlpha(25), blurRadius: 12)],
                ),
                child: Icon(icon, color: color, size: 20),
              ),
              Positioned(
                top: -4, right: -4,
                child: Container(
                  width: 16, height: 16,
                  decoration: BoxDecoration(
                    color: color,
                    shape: BoxShape.circle,
                  ),
                  child: Center(
                    child: Text('$num',
                        style: const TextStyle(
                            color: Color(0xFF04070F),
                            fontSize: 9,
                            fontWeight: FontWeight.w900)),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Text(label,
              style: const TextStyle(color: cTextSec, fontSize: 9.5),
              textAlign: TextAlign.center),
        ],
      );
}

// ── Quota Slider — wrapped in Material to satisfy Slider's widget requirement ─
class _QuotaSlider extends StatelessWidget {
  final int value;
  final ValueChanged<int> onChanged;
  const _QuotaSlider({required this.value, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: SliderTheme(
        data: SliderThemeData(
          trackHeight: 3.5,
          activeTrackColor: cMint,
          inactiveTrackColor: cBorder,
          thumbColor: cMint,
          overlayColor: cMint.withAlpha(28),
          thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 8),
          overlayShape: const RoundSliderOverlayShape(overlayRadius: 18),
        ),
        child: Slider(
          value: value.toDouble(),
          min: 100, max: 2048, divisions: 19,
          onChanged: (v) => onChanged(v.toInt()),
        ),
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
      padding: const EdgeInsets.fromLTRB(20, 24, 20, 24),
      child: Column(
        children: [
          ArcProgress(
            progress: pct,
            size: 190,
            color: cMint,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                GlowText('${(pct * 100).toInt()}%', color: cMint, size: 38),
                const SizedBox(height: 3),
                Text(l.absorbing,
                    style: const TextStyle(
                        color: cTextSec, fontSize: 11, letterSpacing: 1)),
              ],
            ),
          ),
          const SizedBox(height: 20),
          _StatRow(children: [
            _Stat(_fmt(state.progressMb), l.absorbed, cMint, Icons.download_rounded),
            _Divider(),
            _Stat('${state.sessionRequests}', l.urls, cElectric, Icons.link_rounded),
            _Divider(),
            _Stat('${state.quotaMb}M', l.quota, cTextMid, Icons.data_usage_rounded),
          ]),
          const SizedBox(height: 22),
          NeonButton(
            label: l.stop, icon: Icons.stop_rounded,
            color: cRed, onTap: onStop, outlined: true, height: 52,
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
      padding: const EdgeInsets.fromLTRB(20, 28, 20, 24),
      child: Column(
        children: [
          _ServeOrb(),
          const SizedBox(height: 16),
          GlowText(l.simActive, color: cCyan, size: 22),
          const SizedBox(height: 4),
          Text(
            '${_fmt(state.cacheSizeMb)} · ${state.cacheEntryCount} pages',
            style: const TextStyle(color: cTextSec, fontSize: 12.5),
          ),
          const SizedBox(height: 22),
          _StatRow(children: [
            _Stat(_fmt(state.cacheSizeMb), l.cached, cCyan, Icons.storage_rounded),
            _Divider(),
            _Stat('${state.cacheHits}', l.cacheHits, cMint, Icons.offline_bolt_rounded),
            _Divider(),
            _Stat('${state.cacheEntryCount}', l.urls, cElectric, Icons.link_rounded),
          ]),
          const SizedBox(height: 22),
          NeonButton(
            label: l.stop, icon: Icons.stop_rounded,
            color: cRed, onTap: onStop, outlined: true, height: 52,
          ),
        ],
      ),
    );
  }
}

class _ServeOrb extends StatefulWidget {
  @override
  State<_ServeOrb> createState() => _ServeOrbState();
}

class _ServeOrbState extends State<_ServeOrb> with SingleTickerProviderStateMixin {
  late AnimationController _ctrl;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(vsync: this, duration: const Duration(seconds: 2))
      ..repeat(reverse: true);
  }

  @override
  void dispose() { _ctrl.dispose(); super.dispose(); }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _ctrl,
      builder: (_, __) => Container(
        width: 96, height: 96,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          color: cCyan.withAlpha(18),
          border: Border.all(color: cCyan.withAlpha(80), width: 2),
          boxShadow: [
            BoxShadow(
              color: cCyan.withAlpha((40 + 50 * _ctrl.value).toInt()),
              blurRadius: 24 + 20 * _ctrl.value,
              spreadRadius: 2 + 3 * _ctrl.value,
            ),
          ],
        ),
        child: Icon(Icons.sim_card_rounded, color: cCyan, size: 44),
      ),
    );
  }
}

// ── Shared helpers ────────────────────────────────────────────────────────────
class _StatRow extends StatelessWidget {
  final List<Widget> children;
  const _StatRow({required this.children});

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 16),
        decoration: BoxDecoration(
          color: const Color(0xFF060E1C),
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: cBorder),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
          children: children,
        ),
      );
}

class _Stat extends StatelessWidget {
  final String value, label;
  final Color color;
  final IconData icon;
  const _Stat(this.value, this.label, this.color, this.icon);

  @override
  Widget build(BuildContext context) => Column(
        children: [
          Icon(icon, color: color, size: 17),
          const SizedBox(height: 5),
          GlowText(value, color: color, size: 18, weight: FontWeight.w800),
          const SizedBox(height: 2),
          Text(label, style: const TextStyle(color: cTextSec, fontSize: 9.5)),
        ],
      );
}

class _Divider extends StatelessWidget {
  @override
  Widget build(BuildContext context) =>
      Container(width: 1, height: 44, color: cBorder);
}

// ── Domain Feed ───────────────────────────────────────────────────────────────
class _DomainFeed extends StatelessWidget {
  final List<MapEntry<String, int>> domains;
  const _DomainFeed({required this.domains});

  @override
  Widget build(BuildContext context) {
    return GlassPanel(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            PulsingDot(color: cMint, size: 7),
            const SizedBox(width: 8),
            const Text('LIVE FEED',
                style: TextStyle(
                    color: cTextSec, fontSize: 10,
                    letterSpacing: 2, fontWeight: FontWeight.w700)),
          ]),
          const SizedBox(height: 12),
          ...domains.take(5).map((e) => Padding(
                padding: const EdgeInsets.symmetric(vertical: 4),
                child: Row(children: [
                  Container(
                    width: 5, height: 5, margin: const EdgeInsets.only(right: 10),
                    decoration: const BoxDecoration(color: cMint, shape: BoxShape.circle),
                  ),
                  Expanded(
                    child: Text(
                      Uri.tryParse(e.key)?.host ?? e.key,
                      style: const TextStyle(color: cTextPrim, fontSize: 12),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  Text('${e.value}',
                      style: const TextStyle(color: cTextSec, fontSize: 10.5)),
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
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          child: Row(children: [
            Container(
              width: 36, height: 36,
              decoration: BoxDecoration(
                color: cElectric.withAlpha(18),
                borderRadius: BorderRadius.circular(9),
              ),
              child: const Icon(Icons.link_rounded, color: cElectric, size: 18),
            ),
            const SizedBox(width: 12),
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
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          child: Row(children: [
            Container(
              width: 36, height: 36,
              decoration: BoxDecoration(
                color: cMint.withAlpha(18),
                borderRadius: BorderRadius.circular(9),
              ),
              child: const Icon(Icons.storage_rounded, color: cMint, size: 18),
            ),
            const SizedBox(width: 12),
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
