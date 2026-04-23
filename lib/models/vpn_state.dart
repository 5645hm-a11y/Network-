enum VpnMode { absorb, serve }

class VpnState {
  final bool running;
  final VpnMode mode;
  final int quotaMb;
  final double progressMb;
  final int sessionRequests;
  final double sessionKb;
  final int cacheHits;
  final int cacheEntryCount;
  final double cacheSizeMb;
  final bool caCertReady;
  final List<MapEntry<String, int>> topDomains;
  final String agentMessage;
  final bool agentThinking;
  final bool agentHasKey;

  const VpnState({
    this.running = false,
    this.mode = VpnMode.absorb,
    this.quotaMb = 500,
    this.progressMb = 0,
    this.sessionRequests = 0,
    this.sessionKb = 0,
    this.cacheHits = 0,
    this.cacheEntryCount = 0,
    this.cacheSizeMb = 0,
    this.caCertReady = false,
    this.topDomains = const [],
    this.agentMessage = '',
    this.agentThinking = false,
    this.agentHasKey = false,
  });

  VpnState copyWith({
    bool? running,
    VpnMode? mode,
    int? quotaMb,
    double? progressMb,
    int? sessionRequests,
    double? sessionKb,
    int? cacheHits,
    int? cacheEntryCount,
    double? cacheSizeMb,
    bool? caCertReady,
    List<MapEntry<String, int>>? topDomains,
    String? agentMessage,
    bool? agentThinking,
    bool? agentHasKey,
  }) => VpnState(
    running: running ?? this.running,
    mode: mode ?? this.mode,
    quotaMb: quotaMb ?? this.quotaMb,
    progressMb: progressMb ?? this.progressMb,
    sessionRequests: sessionRequests ?? this.sessionRequests,
    sessionKb: sessionKb ?? this.sessionKb,
    cacheHits: cacheHits ?? this.cacheHits,
    cacheEntryCount: cacheEntryCount ?? this.cacheEntryCount,
    cacheSizeMb: cacheSizeMb ?? this.cacheSizeMb,
    caCertReady: caCertReady ?? this.caCertReady,
    topDomains: topDomains ?? this.topDomains,
    agentMessage: agentMessage ?? this.agentMessage,
    agentThinking: agentThinking ?? this.agentThinking,
    agentHasKey: agentHasKey ?? this.agentHasKey,
  );

  factory VpnState.fromMap(Map<dynamic, dynamic> m) {
    final domainsRaw = m['topDomains'] as List? ?? [];
    final domains = domainsRaw
        .map((e) => MapEntry<String, int>(e['host'] as String, e['count'] as int))
        .toList();
    return VpnState(
      running: m['running'] as bool? ?? false,
      mode: (m['mode'] as String?) == 'serve' ? VpnMode.serve : VpnMode.absorb,
      quotaMb: m['quotaMb'] as int? ?? 500,
      progressMb: (m['progressMb'] as num?)?.toDouble() ?? 0,
      sessionRequests: m['sessionRequests'] as int? ?? 0,
      sessionKb: (m['sessionKb'] as num?)?.toDouble() ?? 0,
      cacheHits: m['cacheHits'] as int? ?? 0,
      cacheEntryCount: m['cacheEntryCount'] as int? ?? 0,
      cacheSizeMb: (m['cacheSizeMb'] as num?)?.toDouble() ?? 0,
      caCertReady: m['caCertReady'] as bool? ?? false,
      topDomains: domains,
      agentMessage: m['agentMessage'] as String? ?? '',
      agentThinking: m['agentThinking'] as bool? ?? false,
      agentHasKey: m['agentHasKey'] as bool? ?? false,
    );
  }
}

class TrafficEvent {
  final String type; // 'response' | 'hit' | 'miss'
  final String url;
  final int? statusCode;
  final int? sizeBytes;

  const TrafficEvent({
    required this.type,
    required this.url,
    this.statusCode,
    this.sizeBytes,
  });

  factory TrafficEvent.fromMap(Map<dynamic, dynamic> m) => TrafficEvent(
    type: m['type'] as String? ?? 'miss',
    url: m['url'] as String? ?? '',
    statusCode: m['statusCode'] as int?,
    sizeBytes: m['sizeBytes'] as int?,
  );
}

class CacheEntry {
  final String url;
  final String contentType;
  final int sizeBytes;
  final int statusCode;
  final int timestampMs;

  const CacheEntry({
    required this.url,
    required this.contentType,
    required this.sizeBytes,
    required this.statusCode,
    required this.timestampMs,
  });

  factory CacheEntry.fromMap(Map<dynamic, dynamic> m) => CacheEntry(
    url: m['url'] as String? ?? '',
    contentType: m['contentType'] as String? ?? '',
    sizeBytes: m['sizeBytes'] as int? ?? 0,
    statusCode: m['statusCode'] as int? ?? 0,
    timestampMs: m['timestampMs'] as int? ?? 0,
  );
}
