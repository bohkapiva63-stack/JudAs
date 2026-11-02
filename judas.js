(function () {
    'use strict';

    if (window.judas_plugin_loaded) return;
    window.judas_plugin_loaded = true;

    // Регистрация JudAs Player в настройках Лампы
    Lampa.SettingsApi.addParam({
        component: 'player',
        param: {
            name: 'judas_player_use',
            type: 'select',
            values: {
                '0': 'Выключен',
                '1': 'Включен'
            },
            default: '1'
        },
        field: {
            name: 'JudAs Player Интеграция',
            description: 'Прямая передача всех серий сезона в JudAs Player'
        }
    });

    // Перехват клика воспроизведения
    Lampa.Player.listener.follow('ready', function (e) {
        if (Lampa.Storage.field('judas_player_use') == '1') {

            var playlist = Lampa.Player.playlist() || [];
            var current = Lampa.Player.data() || {};

            // Формируем чистый массив серий
            var episodes = playlist.map(function(item, index) {
                return {
                    title: item.title || item.name || ('Серия ' + (index + 1)),
                    url: item.url || item.file || '',
                    poster: item.poster || item.img || ''
                };
            });

            // Формируем Intent для JudAs Player
            var intentData = {
                action: 'com.mxtech.intent.action.VIEW',
                package: 'com.soulwin.player',
                class: 'com.brouken.player.PlayerActivity',
                data: current.url || e.url,
                type: 'video/*',
                extras: {
                    'title': current.title || (Lampa.Activity.active().card ? Lampa.Activity.active().card.title : 'JudAs Player'),
                    'position': Math.round((current.timeline ? current.timeline.time : 0) * 1000),
                    'video_list': JSON.stringify(episodes)
                }
            };

            // Вызов через Android Bridge / Android Intent
            if (window.android && window.android.startActivity) {
                window.android.startActivity(JSON.stringify(intentData));
            } else if (window.Lampa && Lampa.Android) {
                Lampa.Android.openInApp(current.url, 'com.soulwin.player');
            }
        }
    });
})();
