import _ from 'lodash'

const fmtev = function(ev) {
   switch (ev) {
      case   7: return 'Event (7): Over voltage occurence';
      case   8: return 'Event (8): Over voltage restoration';
      case   9: return 'Event (9): Low voltage  occurence';
      case  10: return 'Event (10): Low voltage restoration';
      case  51: return 'Event (51): Reverse occurence';
      case  52: return 'Event (52): Reverse restoration';
      case  67: return 'Event (67): Over current occurence';
      case  68: return 'Event (68): Over current restoration';
      case  69: return 'Event (69): Earth load occurence';
      case  70: return 'Event (70): Earth load restoration';
      case 101: return 'Event (101): Power fail occrence';
      case 102: return 'Event (102): Power fail restoration';
      case 201: return 'Event (201): Magnetic occurence';
      case 202: return 'Event (202): Magnetic restoration';
      case 203: return 'Event (203): Nd occurence';
      case 204: return 'Event (204): Nd restoration';
      case 205: return 'Event (205): Low pf occurence';
      case 206: return 'Event (206): Low pf restoration';
      case 207: return 'Event (207): Neutral miss occurence';
      case 208: return 'Event (208): Neutral miss restoration';
      case 251: return 'Event (251): Cover Open';
      case 301: return 'Event (301): Latch disconnection';
      case 302: return 'Event (302): Latch reconnection';
      default:
         return 'Event (' + ev + '): Unknown'
   }
}

const fmtdt = (dt) => {
   let [yh, yl, m, dom, dow, h, min, sec, hs] = dt
   return [((yh << 8) + yl),
           ("0"+m).slice(-2),
           ("0"+dom).slice(-2)
          ].join('-') + 'T' + [("0"+h).slice(-2),
                               ("0"+min).slice(-2),
                               ("0"+sec).slice(-2)].join(':') + '.' + ("00" + hs).slice(-3)
}


const attrs = {
      "1/0.0.42.0.0.255/2":      [['main'],              'logical-device-name',           null],
      "1/0.0.96.1.0.255/2":      [['main'],              'meter-serial-number',           null],
      "1/0.0.96.1.1.255/2":      [['main'],              'manufacturers-name',            null],
      "1/1.0.0.2.0.255/2":       [['main'],              'firmware-version',              null],
      "1/0.0.94.91.9.255/2":     [['main'],              'meter-type',                    null],
      "1/0.0.94.91.11.255/2":    [['main'],              'category',                      null],
      "1/0.0.94.91.12.255/2":    [['main'],              'current-rating',                null],
      "1/0.0.96.1.4.255/2":      [['main'],              'manufactur-year',               null],
      "1/1.0.0.8.0.255/2":       [['main'],              'demand-integration-period',     null],
      "1/1.0.0.8.4.255/2":       [['main'],              'block-load-integration-period', null],
      "1/1.0.0.8.5.255/2":       [['main'],              'daily-load-capture-period',     null],
      "1/0.0.94.91.0.255/2":     [['main'],              'cumulative-tamper-count',       null],
      "1/0.0.0.1.0.255/2":       [['main', 'billing'],   'cumulative-billing-count',      null],
      "1/0.0.96.2.0.255/2":      [['main'],              'programming-count',             null],
      "1/0.0.96.11.0.255/2":     [['main'],              'event0-code-object',            fmtev],
      "1/0.0.96.11.1.255/2":     [['main'],              'event1-code-object',            fmtev],
      "1/0.0.96.11.2.255/2":     [['main'],              'event2-code-object',            fmtev],
      "1/0.0.96.11.3.255/2":     [['main'],              'event3-code-object',            fmtev],
      "1/0.0.96.11.4.255/2":     [['main'],              'event4-code-object',            fmtev],
      "1/0.0.0.1.1.255/2":       [['main', 'billing'],   'available-billing-cycles',      null],
      "1/0.128.128.0.0.255/2":   [['main', 'configure'], 'over-current-for-cut-off',      null, 'int'],
      "1/0.128.128.0.1.255/2":   [['main', 'configure'], 'over-load-for-cut-off',         null, 'int'],
      "1/0.128.128.0.2.255/2":   [['main', 'configure'], 'connection-period-interval',    null, 'int'],
      "1/0.128.128.0.3.255/2":   [['main', 'configure'], 'connection-lockout-time',       null, 'int'],
      "1/0.128.128.0.4.255/2":   [['main', 'configure'], 'connection-time-repeat',        null, 'int'],
      "1/0.128.128.0.5.255/2":   [['main', 'configure'], 'tamper-occurance-time',         null, 'int'],
      "1/0.128.128.0.6.255/2":   [['main', 'configure'], 'tamper-restoration-time',       null, 'int'],
      "1/0.128.128.0.7.255/2":   [['main', 'configure'], 'force-switch-enable',           null, 'bit'],

      "3/1.0.12.7.0.255/2":      [['main'],            'voltage',                       null],
      "3/1.0.11.7.0.255/2":      [['main'],            'phase-current',                 null],
      "3/1.0.91.7.0.255/2":      [['main'],            'neutral-current',               null],
      "3/1.0.13.7.0.255/2":      [['main'],            'signed-power-factor',           null],
      "3/1.0.14.7.0.255/2":      [['main'],            'frequency',                     null],
      "3/1.0.9.7.0.255/2":       [['main'],            'apparent-power',                null],
      "3/1.0.1.7.0.255/2":       [['main'],            'active-power',                  null],
      "3/1.0.1.8.0.255/2":       [['main'],            'cumulative-active-energy',      null],
      "3/1.0.9.8.0.255/2":       [['main'],            'cumulative-apparent-energy',    null],
      "3/0.0.94.91.14.255/2":    [['main'],            'cumulative-power-on',           null],
      "3/1.0.12.27.0.255/2":     [['main'],            'average-voltage',               null],
      "3/1.0.1.29.0.255/2":      [['main'],            'block-kwh',                     null],
      "3/1.0.9.29.0.255/2":      [['main'],            'bloack-kvah',                   null],
      "3/0.0.0.1.2.255/2":       [['main', 'billing'], 'billing-date',                  fmtdt],
      "3/1.0.13.0.0.255/2":      [['main'],            'bp-average-power-factor',       null],
      "3/1.0.1.8.1.255/2":       [['main'],            'tz1-kwh',                       null],
      "3/1.0.1.8.2.255/2":       [['main'],            'tz2-kwh',                       null],
      "3/1.0.1.8.3.255/2":       [['main'],            'tz3-kwh',                       null],
      "3/1.0.1.8.4.255/2":       [['main'],            'tz4-kwh',                       null],
      "3/1.0.9.8.1.255/2":       [['main'],            'tz1-kvah',                      null],
      "3/1.0.9.8.2.255/2":       [['main'],            'tz2-kvah',                      null],
      "3/1.0.9.8.3.255/2":       [['main'],            'tz3-kvah',                      null],
      "3/1.0.9.8.4.255/2":       [['main'],            'tz4-kvah',                      null],
      "3/1.0.94.91.14.255/2":    [['main'],            'active-current',                null],
      "3/0.0.94.91.13.255/2":    [['main'],            'total-power-on-time',           null],

      "4/1.0.1.6.0.255/2":       [['main'], 'kw-md-with-date-and-time',      null],
      "4/1.0.9.6.0.255/2":       [['main'], 'kva-md-with-date-and-time',     null],

      "7/1.0.94.91.0.255/2":     [['billing_profile'], 'instantaneous-profile',         null],
      "7/1.0.94.91.3.255/2":     [['billing_profile'], 'instantaneous-scaler-profile',  null],
      "7/1.0.99.1.0.255/2":      [['block'],           'block-load-profile',            null],
      "7/1.0.94.91.4.255/2":     [['block'],           'block-load-scaler-profile',     null],
      "7/1.0.99.2.0.255/2":      [['daily'],           'daily-load-profile',            null],
      "7/1.0.94.91.5.255/2":     [['daily'],           'daily-load-scaler-profile',     null],
      "7/1.0.98.1.0.255/2":      [['billing'],         'billing-profile',               null],
      "7/1.0.94.91.6.255/2":     [['billing'],         'billing-scaler-profile',        null],
      "7/1.0.94.91.7.255/2":     [['events'],          'event0-scaler-profile',         null],
      "7/0.0.99.98.0.255/2":     [['events'],          'event0-profile',                null],
      "7/0.0.99.98.1.255/2":     [['events'],          'event1-profile',                null],
      "7/0.0.99.98.2.255/2":     [['events'],          'event2-profile',                null],
      "7/0.0.99.98.3.255/2":     [['events'],          'event3-profile',                null],
      "7/0.0.99.98.4.255/2":     [['events'],          'event4-profile',                null],
      "7/0.0.94.91.10.255/2":    [['name'],            'name-plate-detail',             null],

      "8/0.0.1.0.0.255/2":       [['main'], 'real-time-clock',               fmtdt],

      "15/0.0.40.0.1.255/2":     [['associations'], 'association0',                  null],
      "15/0.0.40.0.2.255/2":     [['associations'], 'association1',                  null],
      "15/0.0.40.0.3.255/2":     [['associations'], 'association2',                  null],

      "20/0.0.13.0.0.255/2":     [['main'], 'activity-calender',             null],

      "22/0.0.15.0.0.255/2":     [['main'], 'single-action-schedule',        null],

      "70/0.0.96.3.10.255/2":    [['main'],              'disconnect-control', null, 'bool'],
      "70/0.0.96.3.10.255@1":    [['main', 'configure'], 'disconnect-meter',   null, 'exec', null, '70/0.0.96.3.10.255/2'],
      "70/0.0.96.3.10.255@2":    [['main', 'configure'], 'reconnect-meter',    null, 'exec', null, '70/0.0.96.3.10.255/2'],
}


const groups = _.reduce(attrs, (acc, v, k) => {
  _.each(v[0], (g) => acc[g] = _.concat(acc[g] || [], [k]))
  return acc
}, {})

export {
   groups,
   attrs
}
