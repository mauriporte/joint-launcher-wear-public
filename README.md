# Joint Launcher for Wear OS

Joint Launcher's look on a round Wear OS watch (built for a Fossil Gen 6, Wear OS 3): near-black, dark orange
`#E8650A`, the flip clock. Everything live comes from Home Assistant.

- **Joint Launcher watch face:** icon, small flip clock, Now Playing pill (song, artist, cover from any HA
  `media_player`, e.g. Spotify), TV buttons. Tappable: the buttons call HA, the pill opens the player.
- **Joint Flip watch face:** big flip clock, weather above, steps and heart rate below, a Sonando line.
- **App:** three screens swiped up/down: flip clock, player (same as the face), notifications.
- **Accesos tile:** shortcuts into MauHomeAssist's screens, Spotify and Now Playing.

## Setup

1. Copy `config.example.json` to `app/src/main/assets/config.json` and fill in the HA addresses and entities
   (`players` for Now Playing, `tv` and `tvRemote` for the TV row).
2. `.\gradlew.bat assembleDebug assembleRelease`
3. Install the debug APK, run `set-token.ps1` and paste an HA long-lived token, then `adb install -r` the release APK
   (it keeps the token).
4. Notifications screen: `adb shell cmd notification allow_listener com.mau.hub.wear/com.mau.hub.wear.Notifs`
5. Open the app once and allow the complication permission (Joint Flip's weather, steps, heart rate).
6. Long-press the watch face to pick **Joint Launcher** or **Joint Flip**; long-press a tile → + → **Accesos**.

## SW3 builds

`wear-legacy` (Sony SmartWatch 3) and `phone-sw3` (its phone helper) read MauHomeAssist's `config.json` from a sibling
checkout of [ha-companion-revamped-public](https://github.com/mauriporte/ha-companion-revamped-public)
(`mauwatch/app/src/main/assets`). See `docs/sw3-ha-relay.md`.

## License

MIT, see `LICENSE`.
