The `termux/termux-app` repository is released under [GPLv3 only](https://www.gnu.org/licenses/gpl-3.0.html) license.

### Exceptions

- [Terminal Emulator for Android](https://github.com/jackpal/Android-Terminal-Emulator) code is used which is released under [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) license. Check [`terminal-view`](terminal-view) and [`terminal-emulator`](terminal-emulator) libraries.
- Color schemes and fonts from [Termux:Styling](https://github.com/termux/termux-styling) are bundled under GPLv3. Individual font and color licenses are included as `.txt` files next to the assets.
- The VPN engine [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) and its dependencies are fetched at the commits in `gradle/third-party-dependencies.json`. Their license files are preserved under `app/build/third-party` when preparing the build.
- Check [`termux-shared/LICENSE.md`](termux-shared/LICENSE.md) for `termux-shared` library related exceptions.
