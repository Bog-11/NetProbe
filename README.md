NetProbe: Professional Network Diagnostic Tool
NetProbe is a powerful, Matrix-themed network analysis and diagnostic utility for Android. Designed for IT professionals, security researchers, and power users, it provides a comprehensive suite of tools to map local networks, identify connected hardware, and perform deep-dive host investigations—all within a high-performance, parallelized environment.


🚀 Key Features
1. Live Network Overview
•
Real-time Monitoring: Instant status updates on connection type (Wi-Fi, VPN, Cellular), internet validation, and metering status.
•
Subnet Mapping: Automatic calculation of CIDR network ranges (e.g., 192.168.1.0/24) and identification of local gateway interfaces.
•
DNS Analysis: Visibility into current active DNS servers and primary device IPs.
2. Deep Network Discovery
•
Parallel Subnet Scanning: High-speed discovery of all active devices on your local network using asynchronous coroutines.
•
Device Fingerprinting: Advanced identification logic that detects device types (iPhone, Mac, Windows, Linux, Printers, IoT) by analyzing specific port signatures.
•
Hostname Resolution: Resolves friendly names and model details to identify ownership and hardware status at a glance.
3. Aggressive Host Probing
•
Port Scanning: Thoroughly scans well-known ports (1-1024) plus high-range services (MySQL, RDP, Proxy, etc.) in seconds.
•
Service Knowledge Base: Integrated database providing detailed descriptions of port functions and common vulnerabilities.
•
One-Tap Browser Integration: Instantly launch discovered web services (HTTP/HTTPS) directly into your default browser.
4. Advanced Diagnostics
•
Matrix-Themed Reports: Collapsable, color-coded diagnostic windows (Red for WHOIS, Purple for Traceroute) with a terminal-inspired aesthetic.
•
Traceroute analysis: Live, hop-by-hop route mapping to visualize the network path between your phone and any target host.
•
Global WHOIS Lookup: Automated registry querying (IANA/ARIN/RIPE) for domain and IP ownership information.
🛠 Technical Stack
•
Language: 100% Kotlin
•
UI Framework: Jetpack Compose (Modern, declarative UI)
•
Concurrency: Kotlin Coroutines & Flow (For high-speed, non-blocking network operations)
•
Architecture: MVVM (Model-View-ViewModel) for clean separation of concerns
•
Networking: Low-level Socket API, InetAddress, and Runtime Process execution for shell-level precision.


🛡 Security & Privacy

NetProbe is built with security as a priority, featuring:
•
Strict Input Sanitization: Prevents shell command injection vulnerabilities.
•
Modern Permission Handling: Respects Android 13+ privacy standards for Location and Nearby Device access.
•
Zero Data Retention: No tracking, no cloud storage, and no data leaves your device.
NetProbe is an essential tool for troubleshooting complex home networks, auditing corporate subnets, or simply discovering what’s hiding on your Wi-Fi.
