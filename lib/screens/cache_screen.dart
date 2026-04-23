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
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final entries = await _svc.getCacheEntries();
      if (mounted) setState(() { _entries = entries; _loading = false; });
    } catch (_) {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;

    return Scaffold(
      backgroundColor: bgDeep,
      body: SafeArea(
        child: Column(
          children: [
            Row(
              children: [
                IconButton(
                  icon: const Icon(Icons.arrow_back_ios_new_rounded, color: textPrim),
                  onPressed: widget.onBack,
                ),
                Text(l.cacheBrowser,
                    style: const TextStyle(
                        color: textPrim, fontSize: 20, fontWeight: FontWeight.bold)),
                const Spacer(),
                IconButton(
                  icon: const Icon(Icons.refresh_rounded, color: textSec),
                  onPressed: _load,
                ),
              ],
            ),
            Expanded(
              child: _loading
                  ? const Center(child: CircularProgressIndicator(color: electric))
                  : _entries.isEmpty
                      ? Center(child: Text(l.noCacheEntries,
                          style: const TextStyle(color: textSec)))
                      : ListView.builder(
                          padding: const EdgeInsets.symmetric(horizontal: 16),
                          itemCount: _entries.length,
                          itemBuilder: (_, i) => _CacheTile(entry: _entries[i]),
                        ),
            ),
          ],
        ),
      ),
    );
  }
}

class _CacheTile extends StatelessWidget {
  final CacheEntry entry;
  const _CacheTile({required this.entry});

  @override
  Widget build(BuildContext context) {
    final isOk = entry.statusCode < 400;
    final host = Uri.tryParse(entry.url)?.host ?? entry.url;
    final sizeStr = _fmtSize(entry.sizeBytes);

    return Container(
      margin: const EdgeInsets.symmetric(vertical: 3),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: bgCard,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: const Color(0xFF1A2A40)),
      ),
      child: Row(
        children: [
          Container(
            width: 36, height: 36,
            decoration: BoxDecoration(
              color: electric.withAlpha(20),
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Icon(Icons.insert_drive_file_rounded, color: electric, size: 18),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(host,
                    style: const TextStyle(color: textPrim, fontSize: 13),
                    overflow: TextOverflow.ellipsis),
                Text(entry.contentType,
                    style: const TextStyle(color: textSec, fontSize: 11),
                    overflow: TextOverflow.ellipsis),
              ],
            ),
          ),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(sizeStr, style: const TextStyle(color: mint, fontSize: 12)),
              Text('${entry.statusCode}',
                  style: TextStyle(
                      color: isOk ? mint : redStop, fontSize: 11)),
            ],
          ),
        ],
      ),
    );
  }

  String _fmtSize(int bytes) {
    if (bytes >= 1024 * 1024) return '${(bytes / 1024 / 1024).toStringAsFixed(1)} MB';
    if (bytes >= 1024) return '${(bytes / 1024).toStringAsFixed(0)} KB';
    return '$bytes B';
  }
}
