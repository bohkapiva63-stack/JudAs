(function () {
    'use strict';

    if (window.judas_lampa_plugin_loaded) return;
    window.judas_lampa_plugin_loaded = true;

    console.log('JudAs (VLC) Lampa Plugin initialized');

    var JudasPlayer = {
        name: 'JudAs Player',
        version: '1.0.0',
        endpoint: 'http://localhost:8080/requests/lampa.json',

        getStatus: function (callback) {
            fetch(this.endpoint)
                .then(function(res) { return res.json(); })
                .then(function(data) { if (callback) callback(data); })
                .catch(function(err) { console.error('Judas getStatus error:', err); });
        },

        sendCommand: function (command, params, callback) {
            var url = new URL(this.endpoint);
            url.searchParams.append('command', command);
            if (params) {
                Object.keys(params).forEach(function(k) {
                    url.searchParams.append(k, params[k]);
                });
            }
            fetch(url.toString())
                .then(function(res) { return res.json(); })
                .then(function(data) { if (callback) callback(data); })
                .catch(function(err) { console.error('Judas sendCommand error:', err); });
        },

        seekTimecode: function (seconds) {
            this.sendCommand('timecode', { val: seconds });
        },

        selectQuality: function (trackId) {
            this.sendCommand('select_quality', { val: trackId });
        },

        playTorrentStream: function (streamUrl, title) {
            this.sendCommand('torrserver_play', { input: streamUrl, name: title || 'Torrent Stream' });
        }
    };

    window.JudasPlayer = JudasPlayer;

    if (window.Lampa) {
        Lampa.Listener.follow('player', function (e) {
            if (e.type === 'timeupdate' && e.duration > 0) {
                // Sync timecode back to Lampa
                Lampa.Timeline.update({
                    hash: e.hash,
                    time: e.time,
                    duration: e.duration,
                    percent: (e.time / e.duration) * 100
                });
            }
        });
    }
})();
