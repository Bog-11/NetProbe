# Task List - Security & UI Improvements

## UI Improvements
- [ ] Update `HostProbeUiState` to track probe type (Quick vs Aggressive)
- [ ] Update `HostProbeViewModel` to set probe type state
- [ ] Refactor `HostProbeScreen` to show a single, large "Probing..." indicator

## Security Hardening
- [ ] Add `SecurityWarningDialog` to `LiveConnectionsScreen`
- [ ] Add `SANITIZED_PCAP` mode to `ExportMode` and `LiveConnectionsViewModel`
- [ ] Implement payload stripping in `RawPcapExporter`
- [ ] Add "Sanitized PCAP" option to `LiveConnectionsScreen`
- [ ] Add bounds checking to `PacketRewriter` and `ReconstructedPcapExporter`
- [ ] Modernize `NEARBY_WIFI_DEVICES` permission in `AndroidManifest.xml`
