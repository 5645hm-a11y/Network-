import 'package:flutter/material.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import 'package:provider/provider.dart';
import '../models/vpn_state.dart';
import '../providers/vpn_provider.dart';
import '../theme/app_theme.dart';
import '../widgets/arc_progress.dart';

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
    final l = AppLocalizations.of(context)!;
    final vp = context.watch<VpnProvider>();
    final s = vp.state;

    return Scaffold(
      backgroundColor: bgDeep,
      body: Stack(
        children: [
          _buildBackground(),
          SafeArea(
            child: Column(
              children: [
                _buildTopBar(context, l, s),
                Expanded(
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: Column(
                      children: [
                        const SizedBox(height: 8),
                        _AiAgentPanel(state: s),
                        const SizedBox(height: 16),
                        _MainCard(
                          state: s,
                          quotaMb: _quotaMb,
                          onQuotaChanged: (v) => setState(() => _quotaMb = v),
                          onAbsorb: () => vp.startAbsorb(_quotaMb),
                          onServe: () => vp.startServe(),
                          onStop: () => vp.stop(),
                          l: l,
                        ),
                        if (s.running && s.mode == VpnMode.absorb && s.topDomains.isNotEmpty) ...[
                          const SizedBox(height: 12),
                          _DomainFeed(domains: s.topDomains, l: l),
                        ],
                        const SizedBox(height: 12),
                        _StatsRow(state: s, l: l),
                        const SizedBox(height: 24),
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

  Widget _buildBackground() {
    return CustomPaint(
      painter: _BgPainter(),
      child: const SizedBox.expand(),
    );
  }

  Widget _buildTopBar(BuildContext context, AppLocalizations l, VpnState s) {
    Color dotColor;
    if (!s.running) dotColor = textSec;
    else if (s.mode == VpnMode.absorb) dotColor = mint;
    else dotColor = cyanSim;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Row(
        children: [
          Container(
            width: 8, height: 8,
            decoration: BoxDecoration(color: dotColor, shape: BoxShape.circle),
          ),
          const SizedBox(width: 8),
          Text(l.appTitle,
              style: const TextStyle(color: textPrim, fontSize: 18, fontWeight: FontWeight.bold)),
          const Spacer(),
          IconButton(
            icon: const Icon(Icons.bar_chart_rounded, color: textSec),
            onPressed: widget.onTraffic,
          ),
          IconButton(
            icon: const Icon(Icons.storage_rounded, color: textSec),
            onPressed: widget.onCache,
          ),
          IconButton(
            icon: const Icon(Icons.settings_rounded, color: textSec),
            onPressed: widget.onSettings,
          ),
        ],
      ),
    );
  }
}

class _BgPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawRect(Rect.fromLTWH(0, 0, size.width, size.height),
        Paint()..color = bgDeep);
    canvas.drawCircle(
      Offset(size.width * 0.8, size.height * 0.1),
      160,
      Paint()..color = electric.withAlpha(18),
    );
    canvas.drawCircle(
      Offset(size.width * 0.1, size.height * 0.6),
      120,
      Paint()..color = violetAI.withAlpha(18),
    );
  }

  @override
  bool shouldRepaint(_) => false;
}

// ── AI Agent Panel ─────────────────────────────────────────────────────────────
class _AiAgentPanel extends StatelessWidget {
  final VpnState state;
  const _AiAgentPanel({required this.state});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: const Color(0xFF0E0A1E),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: violetAI.withAlpha(60)),
      ),
      child: Row(
        children: [
          if (state.agentThinking)
            const SizedBox(
              width: 18, height: 18,
              child: CircularProgressIndicator(
                color: violetAI, strokeWidth: 2,
              ),
            )
          else
            const Icon(Icons.psychology_rounded, color: violetAI, size: 18),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              state.agentMessage.isEmpty ? 'Virtual SIM Agent' : state.agentMessage,
              style: const TextStyle(color: textPrim, fontSize: 13),
            ),
          ),
          if (!state.agentHasKey)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                color: amber.withAlpha(30),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: amber.withAlpha(80)),
              ),
              child: const Text('API Key', style: TextStyle(color: amber, fontSize: 10)),
            ),
        ],
      ),
    );
  }
}

// ── Main Card ─────────────────────────────────────────────────────────────────
class _MainCard extends StatelessWidget {
  final VpnState state;
  final int quotaMb;
  final ValueChanged<int> onQuotaChanged;
  final VoidCallback onAbsorb;
  final VoidCallback onServe;
  final VoidCallback onStop;
  final AppLocalizations l;

  const _MainCard({
    required this.state,
    required this.quotaMb,
    required this.onQuotaChanged,
    required this.onAbsorb,
    required this.onServe,
    required this.onStop,
    required this.l,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: bgCard,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: const Color(0xFF1A2A40)),
      ),
      child: AnimatedSwitcher(
        duration: const Duration(milliseconds: 400),
        child: !state.running
            ? _IdleContent(
                key: const ValueKey('idle'),
                quotaMb: quotaMb,
                onQuotaChanged: onQuotaChanged,
                onAbsorb: onAbsorb,
                onServe: onServe,
                l: l,
              )
            : state.mode == VpnMode.absorb
                ? _AbsorbingContent(
                    key: const ValueKey('absorb'),
                    state: state,
                    onStop: onStop,
                    l: l,
                  )
                : _SimActiveContent(
                    key: const ValueKey('serve'),
                    state: state,
                    onStop: onStop,
                    l: l,
                  ),
      ),
    );
  }
}

// ── Idle Content ─────────────────────────────────────────────────────────────
class _IdleContent extends StatelessWidget {
  final int quotaMb;
  final ValueChanged<int> onQuotaChanged;
  final VoidCallback onAbsorb;
  final VoidCallback onServe;
  final AppLocalizations l;

  const _IdleContent({
    super.key,
    required this.quotaMb,
    required this.onQuotaChanged,
    required this.onAbsorb,
    required this.onServe,
    required this.l,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Step flow
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
          children: [
            _StepDot(icon: Icons.wifi_rounded, label: l.step1, color: electric),
            const Icon(Icons.chevron_right_rounded, color: textSec, size: 16),
            _StepDot(icon: Icons.download_rounded, label: l.step2, color: mint),
            const Icon(Icons.chevron_right_rounded, color: textSec, size: 16),
            _StepDot(icon: Icons.flight_rounded, label: l.step3, color: amber),
            const Icon(Icons.chevron_right_rounded, color: textSec, size: 16),
            _StepDot(icon: Icons.language_rounded, label: l.step4, color: cyanSim),
          ],
        ),
        const SizedBox(height: 20),

        // Quota selector
        Row(
          children: [
            Text(l.quota, style: const TextStyle(color: textSec, fontSize: 13)),
            const Spacer(),
            Text('${quotaMb} MB',
                style: const TextStyle(color: mint, fontSize: 15, fontWeight: FontWeight.bold)),
          ],
        ),
        SliderTheme(
          data: SliderTheme.of(context).copyWith(
            activeTrackColor: mint,
            inactiveTrackColor: bgSurface,
            thumbColor: mint,
            overlayColor: mint.withAlpha(30),
          ),
          child: Slider(
            value: quotaMb.toDouble(),
            min: 100,
            max: 2048,
            divisions: 19,
            onChanged: (v) => onQuotaChanged(v.toInt()),
          ),
        ),
        const SizedBox(height: 16),

        // Absorb button
        SizedBox(
          width: double.infinity,
          height: 52,
          child: ElevatedButton.icon(
            style: ElevatedButton.styleFrom(
              backgroundColor: mint,
              foregroundColor: bgDeep,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
            ),
            onPressed: onAbsorb,
            icon: const Icon(Icons.download_rounded),
            label: Text(l.startAbsorbing,
                style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
          ),
        ),
        const SizedBox(height: 10),

        // Virtual SIM button
        SizedBox(
          width: double.infinity,
          height: 48,
          child: OutlinedButton.icon(
            style: OutlinedButton.styleFrom(
              foregroundColor: cyanSim,
              side: const BorderSide(color: cyanSim),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
            ),
            onPressed: onServe,
            icon: const Icon(Icons.sim_card_rounded),
            label: Text(l.startVirtualSim,
                style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600)),
          ),
        ),
      ],
    );
  }
}

class _StepDot extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color color;

  const _StepDot({required this.icon, required this.label, required this.color});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Container(
          width: 36, height: 36,
          decoration: BoxDecoration(
            color: color.withAlpha(25),
            borderRadius: BorderRadius.circular(10),
            border: Border.all(color: color.withAlpha(80)),
          ),
          child: Icon(icon, color: color, size: 18),
        ),
        const SizedBox(height: 4),
        Text(label, style: const TextStyle(color: textSec, fontSize: 9)),
      ],
    );
  }
}

// ── Absorbing Content ─────────────────────────────────────────────────────────
class _AbsorbingContent extends StatelessWidget {
  final VpnState state;
  final VoidCallback onStop;
  final AppLocalizations l;

  const _AbsorbingContent({super.key, required this.state, required this.onStop, required this.l});

  @override
  Widget build(BuildContext context) {
    final pct = state.quotaMb > 0 ? (state.progressMb / state.quotaMb).clamp(0.0, 1.0) : 0.0;
    final pctInt = (pct * 100).toInt();

    return Column(
      children: [
        ArcProgress(
          progress: pct,
          size: 180,
          color: mint,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('$pctInt%',
                  style: const TextStyle(color: mint, fontSize: 32, fontWeight: FontWeight.bold)),
              Text(l.absorbing, style: const TextStyle(color: textSec, fontSize: 12)),
            ],
          ),
        ),
        const SizedBox(height: 16),
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
          children: [
            _StatChip(
              icon: Icons.download_rounded,
              value: _fmtMb(state.progressMb),
              label: l.absorbed,
              color: mint,
            ),
            _StatChip(
              icon: Icons.link_rounded,
              value: '${state.sessionRequests}',
              label: l.urls,
              color: electric,
            ),
          ],
        ),
        const SizedBox(height: 16),
        SizedBox(
          width: double.infinity,
          height: 48,
          child: OutlinedButton.icon(
            style: OutlinedButton.styleFrom(
              foregroundColor: redStop,
              side: const BorderSide(color: redStop),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
            ),
            onPressed: onStop,
            icon: const Icon(Icons.stop_rounded),
            label: Text(l.stop, style: const TextStyle(fontWeight: FontWeight.bold)),
          ),
        ),
      ],
    );
  }
}

// ── Sim Active Content ────────────────────────────────────────────────────────
class _SimActiveContent extends StatelessWidget {
  final VpnState state;
  final VoidCallback onStop;
  final AppLocalizations l;

  const _SimActiveContent({super.key, required this.state, required this.onStop, required this.l});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Container(
          width: 80, height: 80,
          decoration: BoxDecoration(
            color: cyanSim.withAlpha(25),
            borderRadius: BorderRadius.circular(20),
            border: Border.all(color: cyanSim.withAlpha(80)),
            boxShadow: [BoxShadow(color: cyanSim.withAlpha(50), blurRadius: 20, spreadRadius: 4)],
          ),
          child: const Icon(Icons.sim_card_rounded, color: cyanSim, size: 40),
        ),
        const SizedBox(height: 12),
        Text(l.simActive,
            style: const TextStyle(color: cyanSim, fontSize: 20, fontWeight: FontWeight.bold)),
        const SizedBox(height: 4),
        Text('${_fmtMb(state.cacheSizeMb)} · ${state.cacheEntryCount} pages',
            style: const TextStyle(color: textSec, fontSize: 13)),
        const SizedBox(height: 16),
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
          children: [
            _StatChip(
              icon: Icons.storage_rounded,
              value: _fmtMb(state.cacheSizeMb),
              label: l.cached,
              color: cyanSim,
            ),
            _StatChip(
              icon: Icons.offline_bolt_rounded,
              value: '${state.cacheHits}',
              label: l.cacheHits,
              color: mint,
            ),
          ],
        ),
        const SizedBox(height: 16),
        SizedBox(
          width: double.infinity,
          height: 48,
          child: OutlinedButton.icon(
            style: OutlinedButton.styleFrom(
              foregroundColor: redStop,
              side: const BorderSide(color: redStop),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
            ),
            onPressed: onStop,
            icon: const Icon(Icons.stop_rounded),
            label: Text(l.stop, style: const TextStyle(fontWeight: FontWeight.bold)),
          ),
        ),
      ],
    );
  }
}

class _StatChip extends StatelessWidget {
  final IconData icon;
  final String value;
  final String label;
  final Color color;

  const _StatChip({required this.icon, required this.value, required this.label, required this.color});

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Icon(icon, color: color, size: 20),
        const SizedBox(height: 4),
        Text(value,
            style: TextStyle(color: color, fontSize: 18, fontWeight: FontWeight.bold)),
        Text(label, style: const TextStyle(color: textSec, fontSize: 11)),
      ],
    );
  }
}

// ── Domain Feed ───────────────────────────────────────────────────────────────
class _DomainFeed extends StatelessWidget {
  final List<MapEntry<String, int>> domains;
  final AppLocalizations l;

  const _DomainFeed({required this.domains, required this.l});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: bgCard,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFF1A2A40)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(l.domainFeed,
              style: const TextStyle(color: textSec, fontSize: 11, letterSpacing: 1.2)),
          const SizedBox(height: 8),
          ...domains.take(6).map((e) => Padding(
                padding: const EdgeInsets.symmetric(vertical: 3),
                child: Row(
                  children: [
                    Container(
                      width: 6, height: 6,
                      margin: const EdgeInsets.only(right: 8),
                      decoration:
                          const BoxDecoration(color: mint, shape: BoxShape.circle),
                    ),
                    Expanded(
                      child: Text(
                        Uri.tryParse(e.key)?.host ?? e.key,
                        style: const TextStyle(color: textPrim, fontSize: 12),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    Text('${e.value}',
                        style: const TextStyle(color: textSec, fontSize: 11)),
                  ],
                ),
              )),
        ],
      ),
    );
  }
}

// ── Stats Row ─────────────────────────────────────────────────────────────────
class _StatsRow extends StatelessWidget {
  final VpnState state;
  final AppLocalizations l;

  const _StatsRow({required this.state, required this.l});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: _StatCard(
            label: l.urls,
            value: '${state.cacheEntryCount}',
            icon: Icons.link_rounded,
            color: electric,
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: _StatCard(
            label: l.mb,
            value: _fmtMb(state.cacheSizeMb),
            icon: Icons.storage_rounded,
            color: mint,
          ),
        ),
      ],
    );
  }
}

class _StatCard extends StatelessWidget {
  final String label;
  final String value;
  final IconData icon;
  final Color color;

  const _StatCard({required this.label, required this.value, required this.icon, required this.color});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: bgCard,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: color.withAlpha(40)),
      ),
      child: Row(
        children: [
          Icon(icon, color: color, size: 20),
          const SizedBox(width: 10),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(value, style: TextStyle(color: color, fontSize: 18, fontWeight: FontWeight.bold)),
              Text(label, style: const TextStyle(color: textSec, fontSize: 11)),
            ],
          ),
        ],
      ),
    );
  }
}

String _fmtMb(double mb) {
  if (mb >= 1024) return '${(mb / 1024).toStringAsFixed(1)} GB';
  if (mb >= 1) return '${mb.toStringAsFixed(0)} MB';
  return '${(mb * 1024).toStringAsFixed(0)} KB';
}
