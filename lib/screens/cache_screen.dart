import 'package:flutter/material.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';
import '../models/vpn_state.dart';
import '../services/vpn_service.dart';
import '../theme/app_theme.dart';

class CacheScreen extends StatefulWidget {
  final VoidCallback onBack;
  const CacheScreen({super.key, required this.onBack});

  @override
  State<CacheScreen> createState() => _CacheScreenState();
}

class _CacheScreenState extends State<CacheScreen> {
  final _svc = VpnService();
  List<CacheEntry> _entries = [];
  bool _loading = true;

  @override
  void initState() { super.initState(); _load(); }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final e = await _svc.getCacheEntries();
      if (mounted) setState(() { _entries = e; _loading = false; });
    } catch (_) {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;

    return Material(
      color: Colors.transparent,
      child: Container(
      decoration: BoxDecoration(gradient: bgGradient),
      child: SafeArea(
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 10),
              child: Row(children: [
                GestureDetector(
                  onTap: widget.onBack,
                  behavior: HitTestBehavior.opaque,
                  child: const Padding(
                    padding: EdgeInsets.all(8),
                    child: Icon(Icons.arrow_back_ios_new_rounded,
                        color: cTextPrim, size: 20),
                  ),
                ),
                const SizedBox(width: 4),
                Text(l.cacheBrowser,
                    style: const TextStyle(
                        color: cTextPrim, fontSize: 19, fontWeight: FontWeight.w700)),
                const Spacer(),
                GestureDetector(
                  onTap: _load,
                  behavior: HitTestBehavior.opaque,
                  child: const Padding(
                    padding: EdgeInsets.all(8),
                    child: Icon(Icons.refresh_rounded, color: cTextSec, size: 22),
                  ),
                ),
              ]),
            ),
            Expanded(
              child: _loading
                  ? const Center(
                      child: CircularProgressIndicator(
                          color: cElectric, strokeWidth: 1.5))
                  : _entries.isEmpty
                      ? Center(child: Text(l.noCacheEntries,
                          style: const TextStyle(color: cTextSec, fontSize: 14)))
                      : ListView.builder(
                          physics: const BouncingScrollPhysics(),
                          padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
                          itemCount: _entries.length,
                          itemBuilder: (_, i) => _CacheTile(entry: _entries[i]),
                        ),
            ),
          ],
        ),
      ),
    ));
  }
}

class _CacheTile extends StatelessWidget {
  final CacheEntry entry;
  const _CacheTile({required this.entry});

  @override
  Widget build(BuildContext context) {
    final host = Uri.tryParse(entry.url)?.host ?? entry.url;
    final ok   = entry.statusCode < 400;

    return Container(
      margin: const EdgeInsets.symmetric(vertical: 3),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: cSurface,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: cBorder),
      ),
      child: Row(children: [
        Container(
          width: 34, height: 34,
          decoration: BoxDecoration(
            color: cElectric.withAlpha(18),
            borderRadius: BorderRadius.circular(8),
          ),
          child: const Icon(Icons.insert_drive_file_rounded,
              color: cElectric, size: 16),
        ),
        const SizedBox(width: 10),
        Expanded(child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(host,
                style: const TextStyle(color: cTextPrim, fontSize: 12.5),
                overflow: TextOverflow.ellipsis),
            Text(entry.contentType,
                style: const TextStyle(color: cTextSec, fontSize: 10.5),
                overflow: TextOverflow.ellipsis),
          ],
        )),
        const SizedBox(width: 8),
        Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
          Text(_fmtB(entry.sizeBytes),
              style: const TextStyle(color: cMint, fontSize: 11)),
          Text('${entry.statusCode}',
              style: TextStyle(
                  color: ok ? cMint : cRed, fontSize: 10.5)),
        ]),
      ]),
    );
  }

  String _fmtB(int b) {
    if (b >= 1048576) return '${(b / 1048576).toStringAsFixed(1)} MB';
    if (b >= 1024)    return '${(b / 1024).toStringAsFixed(0)} KB';
    return '$b B';
  }
}
