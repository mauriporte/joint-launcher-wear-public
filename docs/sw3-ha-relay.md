# HA on the SWR50 through the phone

A phone helper relays Home Assistant to the Sony SmartWatch 3, which then mimics MauHomeAssist's screens and shortcuts.

## Plan

- **Transport:** relay the HA WebSocket instead of single REST calls. The phone helper keeps the authenticated WebSocket, and the watch sends and receives raw WS JSON over `/ha/ws`. That way MauHomeAssist's protocol code (Ha.kt, Dash.kt: lovelace/config, subscribe_entities, call_service, conversation/process) carries over almost unchanged. Risk: the Data Layer limits each message to about 100 KB, so a large `lovelace/config` reply may need chunking.
- **One package for everything:** the face, the HA screens and the phone helper all use `com.mau.hub.wear.sw3` with the same key.
- **UI:** plain Jetpack Compose works on API 23 (minSdk 21), while Wear Compose (minSdk 25) doesn't. So the screens are MauHomeAssist's, rebuilt with plain Compose stand-ins for the Wear components, for the square screen.
- **Phase 1:** Home, Favoritos, Áreas, Menús, toggles with confirm, the alarm prompt, Resúmenes, the 8-button ring as a screen, and the face's MauHomeAssist button.
- **Phase 2:** the voice assistant as text only (conversation/process), Chats and Cámaras (images over Bluetooth are slow).
- **Not possible:** voice (no audio streaming over the 2016 Data Layer) and Tiles (Wear 1.5 has none).

## Why a relay

While the SWR50 (Android Wear 1.5) is paired over Bluetooth, its Wi-Fi is on but disconnected. TCP from the watch to HA (e.g. 192.168.1.10:8123) times out, because its only network is the phone's Bluetooth proxy. The Data Layer (play-services-wearable 8.4.0) does reach the phone, so HA goes that way.

## Messages

| Path | From | Body | What happens |
|---|---|---|---|
| `/ha/call` | watch | `{domain, service, entity_id, data?}` | phone POSTs `/api/services/<domain>/<service>` |
| `/ha/state` | watch | `{entity_id}` | phone GETs `/api/states/<entity_id>` |
| `/ha/result` | phone | `{ok, state?, error?}` | the watch shows it |

Every message also carries an `{id}` that the reply echoes back, because replies can arrive out of order. Everything stays under `/ha`.

## Constraints

- The Data Layer only connects apps that have the same applicationId and signing key, so the phone helper (`phone-sw3`) shares the face's package and key. Other phone apps stay free of HA credentials.
- The HA URL and a long-lived token live only on the phone, entered there by hand (paste or QR). They're never sent to the watch and never go in a repo or `local.properties`.
