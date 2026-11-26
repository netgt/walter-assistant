# Реальные действия приложений на IQOO 13

Снято с устройства: 2026-11-26.
Основание: `cmd package query-activities` и `dumpsys package` (intent-filters).
Для остальных действий — только главный экран. Платежи и необратимые шаги не автоматизируются.

Launcher-пакетов: **103**. Схемы найдены у **96** пакетов.

| Приложение | Пакет | Launcher activity | Действие по intent-filters |
|---|---|---|---|
| AdGuard Mini | `com.adguard.android.contentblocker` | `.entrypoints.mobile_app.Launcher` | Deep link: abp, adg, adguardmini, sdns |
| Albums | `com.vivo.gallery` | `com.android.gallery3d.vivo.GalleryTabActivity` | Deep link: gallery, geo.action, photoeditor, vivogallery |
| Altyn Wallet | `com.altyn.wallet` | `.MainActivity` | Deep link: altynwallet, com.altyn.wallet, exp+altyn-wallet |
| AmneziaVPN | `org.amnezia.vpn` | `.AmneziaActivity` | Deep link: vpn |
| AppGallery | `com.huawei.appmarket` | `.MainActivity` | Deep link: appmarket, hiapp, hiapplink, kids, market, store, tencent101855294 |
| Authenticator | `com.google.android.apps.authenticator2` | `.main.MainActivity` | Deep link: otpauth |
| Calculator | `com.vivo.calculator` | `com.android.bbkcalculator.Calculator` | Deep link: calculator |
| Camera | `com.android.camera` | `.CameraActivity` | Deep link: shortcut |
| Chrome | `com.android.chrome` | `com.google.android.apps.chrome.Main` | Deep link: about, googlechrome |
| Clock | `com.android.deskclock` | `com.android.BBKClock.Timer` | Deep link: bbkclock |
| CloudEdge | `com.cloudedge.smarteye` | `com.ppstrong.weeye.SplashActivity` | Deep link: meari_cloudedge, vpushscheme |
| com.netgt.magicfield | `com.netgt.magicfield` | `.MainActivity` | Главный экран приложения |
| Compass | `com.vivo.compass` | `.CalibrationActivity` | Главный экран приложения |
| DeepSeek | `com.deepseek.chat` | `.MainActivity` | Deep link: com.deepseek.chat, dpsk |
| Docmed | `ru.docmed.app` | `.MainActivity` | Deep link: docmed-app, intent, sbp |
| docmed online | `ru.docmedonline.app` | `.MainActivity` | Deep link: telemeddocmed |
| EasyShare | `com.vivo.easyshare` | `.activity.SplashScreenActivity` | Новое SMS через smsto: |
| Feedback | `com.vivo.feedback` | `.feedback.complain.ComplainActivity` | Deep link: vivofeedback |
| File Manager | `com.android.filemanager` | `.FileManagerActivity` | Главный экран приложения |
| Files | `com.google.android.apps.nbu.files` | `.home.HomeActivity` | Deep link: fbg-app |
| Freedom Banker | `kz.bankffin.app` | `.FreedomMainActivity` | Deep link: ffbankapp |
| Gmail | `com.google.android.gm` | `.ConversationListActivityGmail` | Входящие и новое письмо через mailto: |
| GoDelta | `ru.albertsoft.acabinet` | `.DeltaActivity` | Deep link: spaysdk |
| Google | `com.google.android.googlequicksearchbox` | `.SearchActivity` | Deep link: android-app, assistant-handoff, assistant-settings, com.google.android.apps.gsa.gal, dynact, google, google-wsa, googleapp… |
| Google Play | `com.android.vending` | `.AssetBrowserActivity` | Deep link: google-orchestration, iabData, market, package |
| Hik-Connect | `com.connect.enduser` | `com.hikvision.hikconnect.login.LoadingActivity` | Deep link: HcForSiemens, growing.a106f72190cc8224, growing.b0381989b16832cd, gvforone, hcformozae, hcforsiemens, hikconnect, hikconnectauth |
| Hommyn | `com.hommyn.app` | `com.syncleoiot.app.ui.MainActivity` | Deep link: climaton, genericidp, recaptcha, rusklimat |
| iFEEL Safe+Smart | `com.ifeelsystems.safesmart` | `com.smart.ThingSplashActivity` | Deep link: amzn, ifeelsystemssafesmart, module.entrance |
| iManager | `com.vivo.imanager` | `com.iqoo.secure.MainActivity` | Deep link: imanager, iqoo |
| inskam | `com.ypcc.otgcamera` | `com.ucamera.application.initframe.ListenPopupActivity` | Главный экран приложения |
| INSNC | `by.alfabank.insync3` | `by.alfabank.insync.presentation.feature.splash.activity.SplashActivity` | Deep link: insnc, tagmanager.c.by.alfabank.insync3 |
| iQOO Store | `com.iqoo.website` | `com.vivo.website.activity.SplashActivity` | Deep link: website |
| Kimi | `com.moonshot.kimichat` | `.MainActivity` | Deep link: kimi |
| KOJIMA HOME | `com.kojima.home` | `com.smart.ThingSplashActivity` | Deep link: amzn, kojimahome, module.entrance, recordnote |
| KWIKPAY | `com.kwikpay.transfers` | `com.ltech.unistream.presentation.root.RootActivity` | Deep link: kwikpay |
| Lock | `com.android.bbk.lockscreen3` | `.LockScreenActivity` | Главный экран приложения |
| Loop | `ru.loop.app` | `.MainActivity` | Deep link: loop, ru.loop.app, ru.loop.app.auth |
| MAX | `ru.oneme.app` | `one.me.android.MainActivity` | Deep link: max |
| Meet | `com.google.android.apps.tachyon` | `.MainActivity` | Входящие и новое письмо через mailto: |
| Meshtastic | `com.geeksville.mesh` | `org.meshtastic.app.MainActivity` | Deep link: meshtastic |
| Mir Pay | `ru.nspk.mirpay` | `.olcDxABqhyieE` | Deep link: genericidp, mirpay, mirpay-nav, recaptcha |
| Music | `com.android.bbkmusic` | `.WidgetToTrackActivity` | Deep link: vivomusic |
| Notes | `com.vivo.notes` | `com.android.notes.Notes` | Deep link: bbknotes, notes_user_instructions, vivonotes |
| OpenVPN Connect | `net.openvpn.openvpn` | `net.openvpn.unified.MainActivity` | Deep link: openvpn, openvpn-connect, resource |
| OZON | `ru.ozon.app.android` | `.ui.start.AppHostActivity` | Deep link: bank100000000273, ozon, ozonplatiqr, ozonpush, ozontech, spaysdk, vk53277839 |
| Ozon Банк | `ru.ozon.fintech.finance` | `ru.ozon.fintech.features.tabber.presentation.finance.FinanceActivity` | Набор номера через tel: |
| Phone and Contacts | `com.android.contacts` | `.DialtactsContactsEntryActivity<br>com.android.dialer.TwelveKeyDialer` | Входящие и новое письмо через mailto: |
| Qwen | `com.tongyi.intl` | `ai.qwenlm.chat.android.MainActivity` | Deep link: dws.com_quark_browser, qwen, qwenIntl, qwenintl, ut.35234292 |
| Recorder | `com.vivo.soundrecorder` | `com.android.activity.ReclistActivity` | Deep link: vivo |
| RuStore | `ru.vk.store` | `.DefaultIconAlias` | Deep link: market, ru.vk.store, ru.vk.store.sak, ru.vk.store.vkid, rustore, rustorepermission, vk8186525, yxd97a37b853c343faa960947613be7f12 |
| Simple View | `com.vivo.simplelauncher` | `.SimpleLauncher` | Главный экран приложения |
| Smart Remote | `com.vivo.vhome` | `.ir.ui.IRDeviceManageActivity` | Deep link: vhome, vhome_open_source_declaration |
| SmartMed Medsi | `ru.mts.smartmed` | `ru.smartmed.activities.start.StartActivity` | Deep link: ru.mts.smartmed.paysdksbpreturn, spaysdk |
| Snapseed | `com.niksoftware.snapseed` | `com.google.android.apps.snapseed.WelcomeScreenActivity` | Главный экран приложения |
| Tapo | `com.tplink.iot` | `.view.welcome.StartupActivity` | Deep link: tapo |
| Telegram | `org.telegram.messenger` | `.DefaultIcon` | Deep link: tc, tg, tgb, ton, tonsite |
| Themes | `com.bbk.theme` | `.Theme` | Deep link: newthemedetail, themeaipet, themedetail, themelocal, thememain, themeprivacypolicy, themereslist, themesearch… |
| Tumblr | `com.tumblr` | `.ui.activity.JumpoffActivity` | Deep link: 1074146036303-hnnsl1537iscc8ts01dk1jbqsbu0lsm8.apps.googleusercontent.com, 1074146036303-if4edbigfnd409oh190olj3hkgeinrdo.apps.googleusercontent.com, com.googleusercontent.apps.1074146036303-dcgh2fa53jgoo8qavhv11h3pro4asf5i, tumblr, tumblr-auth |
| V-Appstore | `com.vivo.appstore` | `.activity.LaunchActivity` | Deep link: appstore, gomarket, himarket, market, mobomarket, nineapps, vivoMarket, vivomarket |
| Video | `com.android.VideoPlayer` | `.VideoPlayer` | Deep link: rtsp |
| vivo Cloud | `com.bbk.cloud` | `.activities.BBKCloudHomeScreen` | Deep link: vivocloud |
| VK Видео | `com.vk.vkvideo` | `com.vk.video.screens.main.MainActivity` | Deep link: com.vk.vkvideo, com.vk.vkvideo.sak, vk51452549, vkid-vkvideo, vkvideo |
| Walter | `com.example.dispatcher` | `.MainActivity` | Главный экран приложения |
| Warmme | `com.thermtouch.smart` | `com.smart.ThingSplashActivity` | Deep link: amzn, module.entrance, warmme |
| Weather | `com.vivo.weather` | `.WeatherMain` | Deep link: weather, weather_user_instructions |
| Xiaomi Home | `com.xiaomi.smarthome` | `.SmartHomeMainActivity` | Deep link: 101410142, amzn, fb690839864422812, fbconnect, micoSdk, mihome, mihomeplugin, mijia… |
| YouTube | `com.google.android.youtube` | `.app.honeycomb.Shell$HomeActivity` | Deep link: cardboard, com.google.android.apps.youtube, vnd.youtube, vnd.youtube.launch |
| Zapret KVN | `io.github.zapretkvn.android` | `.MainActivity` | Главный экран приложения |
| Авито | `com.avito.android` | `.Launcher` | Deep link: com.avito, okauth, ru.avito, vk52038254 |
| Алиса AI | `com.yandex.aliceapp` | `.ui.MainActivity` | Deep link: aliceapp, aliceapp-tests, com.yandex.aliceapp.passport, plus |
| Альфа-Банк | `ru.alfabank.mobile.android` | `.splash.presentation.activity.SplashActivity` | Deep link: aid, alfabank, bank100000000008, esia |
| Безопасность | `com.google.android.apps.safetyhub` | `.LauncherActivity` | Набор номера через tel: |
| Браузер | `com.vivo.browser` | `.BrowserActivity` | Deep link: about, geo.action, inline, javascript, vivobrowser, vivovideobrowser |
| ВкусВилл | `ru.vkusvill` | `.ui.MainActivity` | Deep link: vkusvill |
| Госуслуги | `ru.rostel` | `ru.minsvyaz.coreproject.LauncherActivity` | Deep link: gosuslugi |
| Документы | `com.google.android.apps.docs.editors.docs` | `com.google.android.apps.docs.app.NewMainProxyActivity` | Главный экран приложения |
| Здоровье | `com.core.team.renins` | `.feature.AppActivityDefaultIconAlias` | Deep link: renhealth |
| Календарь | `com.google.android.calendar` | `com.android.calendar.AllInOneActivity` | Deep link: geo.action |
| Карты | `com.google.android.apps.maps` | `com.google.android.maps.MapsActivity` | Карта/точка через geo: |
| МегаФон | `ru.megafon.mlk` | `.ui.activities.ActivityMain` | Deep link: inapp, megafonmlk, mlktest |
| МТС Деньги | `ru.lewis.dbo` | `ru.mts.money.activities.MainActivity` | Deep link: bank100000000017, bank100000000289, mdng, mts-sso, mtsmoney |
| Настройки | `com.android.settings` | `.Settings` | Deep link: BLUETOOTH, DPP, bluetooth, package, settings |
| Пароли | `com.kaspersky.passwordmanager` | `.MainActivityLauncher` | Deep link: 552568554846995, fbconnect, kaspersky-passwordmanager-deeplink, kpm, otpauth, yxcec3761398504ef28fac351fe358a043 |
| Презентации | `com.google.android.apps.docs.editors.slides` | `com.google.android.apps.docs.app.NewMainProxyActivity` | Главный экран приложения |
| Профи.ру | `ru.profi.client` | `.MainActivity` | Deep link: profiapp |
| Россети МР | `com.rosseti.rossetimr` | `.MainActivity` | Главный экран приложения |
| РСХБ | `ru.rshb.dbo` | `.MainActivity` | Deep link: bank100000000020, rshbmbfl |
| СберБанк | `ru.sberbankmobile` | `ru.sberbank.mobile.auth.presentation.splash.SplashActivity` | Набор номера через tel: |
| Сообщения | `com.android.mms` | `.ui.ConversationList` | Новое SMS через smsto: |
| Т-Банк | `com.idamob.tinkoff.android` | `ru.tinkoff.mb.feature.splash.bank.ui.presentation.BankSplashActivity` | Набор номера через tel: |
| Т-Мобайл | `ru.tinkoff.mvno` | `ru.tcsbank.mvno.preloader.PreloaderActivity` | Deep link: tinkoffmobile, tmobile |
| Таблицы | `com.google.android.apps.docs.editors.sheets` | `com.google.android.apps.docs.app.NewMainProxyActivity` | Главный экран приложения |
| Фото | `com.google.android.apps.photos` | `.home.HomeActivity` | Deep link: geo.action, gplus, rtsp |
| Цифра банк | `com.bankffin.portfolio` | `.MainActivity` | Deep link: bank100000000265, cifra |
| Я Календарь | `com.yandex.calendar.app` | `.AppActivity` | Deep link: com.yandex.calendar.app.passport, fbconnect |
| Яндекс ID (Ключ) | `ru.yandex.key` | `com.yandex.auth.authenticator.android.ui.activities.MainActivity` | Deep link: fbconnect, otpauth, ru.yandex.key.passport, yandexbank, yandexkey, ybapp |
| Яндекс Браузер | `com.yandex.browser` | `.YandexBrowserActivity` | Deep link: com.yandex.browser.passport, dialog, gallery, messenger, searchlib, setdefault, settings, yandex… |
| Яндекс Диск | `ru.yandex.disk` | `.DefaultIcon` | Deep link: ru.yandex.disk.passport, yadisk, yandexdisk |
| Яндекс Карты | `ru.yandex.yandexmaps` | `.SplashScreen` | Карта/точка через geo: |
| Яндекс Клавиатура | `ru.yandex.androidkeyboard` | `com.android.inputmethod.latin.setup.SetupYandexKeyboardActivityAlias` | Deep link: ru.yandex.androidkeyboard.passport, yandexkeyboard |
| Яндекс Музыка | `ru.yandex.music` | `.main.MainScreenActivity` | Deep link: plus, ru.yandex.music.passport, yandexmusic, yandexradio |
| Яндекс Почта | `ru.yandex.mail` | `.ui.LoginActivity` | Входящие и новое письмо через mailto: |
| ЯндексGo | `ru.yandex.taxi` | `com.yandex.go.activity.alias.DefaultMainActivityAlias` | Карта/точка через geo: |
