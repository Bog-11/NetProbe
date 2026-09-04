# Implementation Plan - Comprehensive Discovery Information

This plan updates the informative windows (dialogs) for "Normal" and "Deep" network discovery to provide a comprehensive explanation of their functions and impacts.

## User Review Required

> [!IMPORTANT]
> - **Content Update**: The technical details in the info dialogs will be significantly expanded to include specific protocols (ARP, ICMP, SNMP, SIP) and techniques (Banner Grabbing, TLS Inspection) used in each mode.
> - **Visual Consistency**: The structure of the information will be standardized using bullet points and categories (Discovery, Scanning, Identification) for better readability.

## Proposed Changes

### [Component Name] UI

#### [MODIFY] [DiscoveryScreen.kt](file:///C:/Users/bogda/AndroidStudioProjects/NetProbe/app/src/main/java/com/brutiful/netprobe/ui/DiscoveryScreen.kt)
- Update the `showInfoDialog` content for `ScanMode.NORMAL` and `ScanMode.DEEP`.
- Update the `showDeepConfirm` dialog text to be more consistent with the new information.

## Information Content

### Normal Discovery
*   **Discovery**: Uses ICMP Pings and ARP requests to find active hosts on the subnet.
*   **Scanning**: Checks 6 essential ports (HTTP, HTTPS, SSH, RTSP) for core services.
*   **Multicast**: Performs one-pass mDNS, SSDP, and ONVIF discovery to identify smart devices, media servers, and IP cameras.
*   **Identification**: NetBIOS name resolution for Windows and Samba shares.
*   **Impact**: Lightweight and safe for most home/office networks.

### Deep Discovery
*   **Comprehensive Scan**: Checks 120+ common ports, including databases (SQL), Remote Desktop (VNC/RDP), and IoT protocols.
*   **Banner Grabbing**: Connects to services to extract version strings, OS information, and device models.
*   **Deep Probing**: Uses SNMP for network infrastructure and SIP for VoIP hardware.
*   **TLS Inspection**: Analyzes SSL/TLS certificates to extract subject names and issuers.
*   **Heuristics**: Multi-pass multicast and RTSP/HTTP header analysis for hidden IoT devices.
*   **Impact**: Heavier network load and increased battery consumption.

## Verification Plan

### Manual Verification
1.  Open the Discovery tab.
2.  Click the Info icon next to "Normal Discovery" and verify the new comprehensive text.
3.  Click the Info icon next to "Deep Discovery" and verify the new comprehensive text.
4.  Click "Deep Discovery" button and verify the confirmation dialog text.
