import React from 'react'
import Network from './Network.jsx'

import {Row, Col, ListGroup, ListGroupItem, Button, Input, FormControl} from 'react-bootstrap'
import {Alert, Glyphicon} from 'react-bootstrap'
import Navigation from './Navigation.jsx'
import {ConnActions, ConnStore, DataStore} from '../API.js'
import {Base64Binary} from '../Base64.js'

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
      case 301: return 'Event (301): Latch disconnection';
      case 302: return 'Event (302): Latch reconnection';
      default:
         return 'Event (' + ev + '): Unknown'
   }
}

const fmt = {
      "1/0.0.42.0.0.255/2":      ['logical-device-name',           null],
      "1/0.0.96.1.0.255/2":      ['meter-serial-number',           null],
      "1/0.0.96.1.1.255/2":      ['manufacturers-name',            null],
      "1/1.0.0.2.0.255/2":       ['firmware-version',              null],
      "1/0.0.94.91.9.255/2":     ['meter-type',                    null],
      "1/0.0.94.91.11.255/2":    ['category',                      null],
      "1/0.0.94.91.12.255/2":    ['current-rating',                null],
      "1/0.0.96.1.4.255/2":      ['manufactur-year',               null],
      "1/1.0.0.8.0.255/2":       ['demand-integration-period',     null],
      "1/1.0.0.8.4.255/2":       ['block-load-integration-period', null],
      "1/1.0.0.8.5.255/2":       ['daily-load-capture-period',     null],
      "1/0.0.94.91.0.255/2":     ['cumulative-tamper-count',       null],
      "1/0.0.0.1.0.255/2":       ['cumulative-billing-count',      null],
      "1/0.0.96.2.0.255/2":      ['programming-count',             null],
      "1/0.0.96.11.0.255/2":     ['event0-code-object',            fmtev],
      "1/0.0.96.11.1.255/2":     ['event1-code-object',            fmtev],
      "1/0.0.96.11.2.255/2":     ['event2-code-object',            fmtev],
      "1/0.0.96.11.3.255/2":     ['event3-code-object',            fmtev],
      "1/0.0.96.11.4.255/2":     ['event4-code-object',            fmtev],
      "1/0.0.0.1.1.255/2":       ['available-billing-cycles',      null],
      "1/0.128.128.0.0.255/2":   ['over-current-for-cut-off',      null, 'int'],
      "1/0.128.128.0.1.255/2":   ['over-load-for-cut-off',         null, 'int'],
      "1/0.128.128.0.2.255/2":   ['connection-period-interval',    null, 'int'],
      "1/0.128.128.0.3.255/2":   ['connection-lockout-time',       null, 'int'],
      "1/0.128.128.0.4.255/2":   ['connection-time-repeat',        null, 'int'],
      "1/0.128.128.0.5.255/2":   ['tamper-occurance-time',         null, 'int'],
      "1/0.128.128.0.6.255/2":   ['tamper-restoration-time',       null, 'int'],
      "1/0.128.128.0.7.255/2":   ['force-switch-enable',           null, 'bit'],
      "3/1.0.12.7.0.255/2":      ['voltage',                       null],
      "3/1.0.11.7.0.255/2":      ['phase-current',                 null],
      "3/1.0.91.7.0.255/2":      ['neutral-current',               null],
      "3/1.0.13.7.0.255/2":      ['signed-power-factor',           null],
      "3/1.0.14.7.0.255/2":      ['frequency',                     null],
      "3/1.0.9.7.0.255/2":       ['apparent-power',                null],
      "3/1.0.1.7.0.255/2":       ['active-power',                  null],
      "3/1.0.1.8.0.255/2":       ['cumulative-active-energy',      null],
      "3/1.0.9.8.0.255/2":       ['cumulative-apparent-energy',    null],
      "3/0.0.94.91.14.255/2":    ['cumulative-power-on',           null],
      "3/1.0.12.27.0.255/2":     ['average-voltage',               null],
      "3/1.0.1.29.0.255/2":      ['block-kwh',                     null],
      "3/1.0.9.29.0.255/2":      ['bloack-kvah',                   null],
      "3/0.0.0.1.2.255/2":       ['billing-date',                  null],
      "3/1.0.13.0.0.255/2":      ['bp-average-power-factor',       null],
      "3/1.0.1.8.1.255/2":       ['tz1-kwh',                       null],
      "3/1.0.1.8.2.255/2":       ['tz2-kwh',                       null],
      "3/1.0.1.8.3.255/2":       ['tz3-kwh',                       null],
      "3/1.0.1.8.4.255/2":       ['tz4-kwh',                       null],
      "3/1.0.9.8.1.255/2":       ['tz1-kvah',                      null],
      "3/1.0.9.8.2.255/2":       ['tz2-kvah',                      null],
      "3/1.0.9.8.3.255/2":       ['tz3-kvah',                      null],
      "3/1.0.9.8.4.255/2":       ['tz4-kvah',                      null],
      "3/1.0.94.91.14.255/2":    ['active-current',                null],
      "3/0.0.94.91.13.255/2":    ['total-power-on-time',           null],
      "4/1.0.1.6.0.255/2":       ['kw-md-with-date-and-time',      null],
      "4/1.0.9.6.0.255/2":       ['kva-md-with-date-and-time',     null],
      "7/1.0.94.91.0.255/2":     ['instantaneous-profile',         null],
      "7/1.0.94.91.3.255/2":     ['instantaneous-scaler-profile',  null],
      "7/1.0.99.1.0.255/2":      ['block-load-profile',            null],
      "7/1.0.94.91.4.255/2":     ['block-load-scaler-profile',     null],
      "7/1.0.99.2.0.255/2":      ['daily-load-profile',            null],
      "7/1.0.94.91.5.255/2":     ['daily-load-scaler-profile',     null],
      "7/1.0.98.1.0.255/2":      ['billing-profile',               null],
      "7/1.0.94.91.6.255/2":     ['billing-scaler-profile',        null],
      "7/1.0.94.91.7.255/2":     ['event0-scaler-profile',         null],
      "7/0.0.99.98.0.255/2":     ['event0-profile',                null],
      "7/0.0.99.98.1.255/2":     ['event1-profile',                null],
      "7/0.0.99.98.2.255/2":     ['event2-profile',                null],
      "7/0.0.99.98.3.255/2":     ['event3-profile',                null],
      "7/0.0.99.98.4.255/2":     ['event4-profile',                null],
      "7/0.0.94.91.10.255/2":    ['name-plate-detail',             null],
      "8/0.0.1.0.0.255/2":       ['real-time-clock',               null],
      "15/0.0.40.0.1.255/2":     ['association0',                  null],
      "15/0.0.40.0.2.255/2":     ['association1',                  null],
      "15/0.0.40.0.3.255/2":     ['association2',                  null],
      "20/0.0.13.0.0.255/2":     ['activity-calender',             null],
      "22/0.0.15.0.0.255/2":     ['single-action-schedule',        null],
      "70/0.0.96.3.10.255/2":    ['disconnect-control',            null, 'bool'],
      "70/0.0.96.3.10.255/5":    ['disconnect-meter',              null, 'exec', '70/0.0.96.3.10.255/2'],
      "70/0.0.96.3.10.255/6":    ['reconnect-meter',               null, 'exec', '70/0.0.96.3.10.255/2'],

}

function ab2str(buf) {
  return String.fromCharCode.apply(null, new Uint16Array(buf));
}

const b64th = (buf) => _.map(Base64Binary.decode(buf), (n) => ("00" + n.toString(16)).slice(-2) ).join(' ')

const maybeFmt = function(k, v, fmt) {
   if (fmt)
      return fmt(v)

   if (_.isBoolean(v))
      return v.toString();

   // str
   if (_.isArray(v))
      return ab2str(v)

   return v
}

export default class Device extends React.Component {
  constructor() {
      super()

      this.state = {
         lines: [],
         loadingReqs: {},
         tab: 'configure',
         pointers: null,
         connState: []
      }


      this._mounted = false
  }

  componentWillMount() {
    let {nid, device} = this.props.params,
        ref = [nid,device].join("/")
    this._mounted = true

    ConnActions.open(ref)
     .then( (conn) => {
         ConnActions.listAttrs(ref)
         this.setState({err: null})
      })
     .catch( (err) => this.setState({err}) )

    ConnStore.addChangeListener(this._changeListener = (ev) => {
      let {connState} = this.state

      if (ConnStore.state(ref) !== connState[0] || connState[0] === undefined)
         this.setState({connState: _.concat([ConnStore.state(ref)], connState).slice(0, 2)})

    })

    DataStore.addChangeListener(this._changeListener2 = (ev) => {
      this.forceUpdate()
      this.setState({
         loadingReqs: DataStore.futures(ref),
         pointers: DataStore.pointers(ref)
      })
    })
  }

  mainAttrs() {
     return [
        "1/0.0.42.0.0.255/2",
        "1/0.0.96.1.0.255/2",
        "1/0.0.96.1.1.255/2",
        "1/1.0.0.2.0.255/2",
        "1/0.0.94.91.9.255/2",
        "1/0.0.94.91.11.255/2",
        "1/0.0.94.91.12.255/2",
        "1/0.0.96.1.4.255/2",
        "1/1.0.0.8.0.255/2",
        "1/1.0.0.8.4.255/2",
        "1/1.0.0.8.5.255/2",
        "1/0.0.94.91.0.255/2",
        "1/0.0.0.1.0.255/2",
        "1/0.0.96.2.0.255/2",
        "1/0.0.96.11.0.255/2",
        "1/0.0.96.11.1.255/2",
        "1/0.0.96.11.2.255/2",
        "1/0.0.96.11.3.255/2",
        "1/0.0.96.11.4.255/2",
        "1/0.0.0.1.1.255/2",
        "1/0.128.128.0.0.255/2",
        "1/0.128.128.0.1.255/2",
        "1/0.128.128.0.2.255/2",
        "1/0.128.128.0.3.255/2",
        "1/0.128.128.0.4.255/2",
        "1/0.128.128.0.5.255/2",
        "1/0.128.128.0.6.255/2",
        "1/0.128.128.0.7.255/2",

        "3/1.0.12.7.0.255/2",
        "3/1.0.11.7.0.255/2",
        "3/1.0.91.7.0.255/2",
        "3/1.0.13.7.0.255/2",
        "3/1.0.14.7.0.255/2",
        "3/1.0.9.7.0.255/2",
        "3/1.0.1.7.0.255/2",
        "3/1.0.1.8.0.255/2",
        "3/1.0.9.8.0.255/2",
        "3/0.0.94.91.14.255/2",
        "3/1.0.12.27.0.255/2",
        "3/1.0.1.29.0.255/2",
        "3/1.0.9.29.0.255/2",
        "3/0.0.0.1.2.255/2",
        "3/1.0.13.0.0.255/2",
        "3/1.0.1.8.1.255/2",
        "3/1.0.1.8.2.255/2",
        "3/1.0.1.8.3.255/2",
        "3/1.0.1.8.4.255/2",
        "3/1.0.9.8.1.255/2",
        "3/1.0.9.8.2.255/2",
        "3/1.0.9.8.3.255/2",
        "3/1.0.9.8.4.255/2",
        "3/1.0.94.91.14.255/2",
        "3/0.0.94.91.13.255/2",

        "4/1.0.1.6.0.255/2",
        "4/1.0.9.6.0.255/2",

        "8/0.0.1.0.0.255/2",

        "20/0.0.13.0.0.255/2",

        "22/0.0.15.0.0.255/2",

        "70/0.0.96.3.10.255/2"
      ]
  }

  nameAttrs() {
    return [
      "7/0.0.94.91.10.255/2", // name plate
    ]
  }

  dailyAttrs() {
    return [
      "7/1.0.99.2.0.255/2",
      "7/1.0.94.91.5.255/2"
    ]
  }

  blockAttrs() {
    return [
      "7/1.0.99.1.0.255/2", // borked, IT'S INFINITE!!
      "7/1.0.94.91.4.255/2", // rinse-repeat
    ]
  }

  billingAttrs() {
    return [
      "7/1.0.98.1.0.255/2", // IOException ,unexpected end of stream
      "7/1.0.94.91.6.255/2", // scaler profile
    ]
  }

  billing_profileAttrs() {
    return [
      "7/1.0.94.91.0.255/2", // borked, Axdr
      "7/1.0.94.91.3.255/2", // borked, 43 pkgs??/
    ]
  }

  eventsAttrs() {
    return [
      "7/1.0.94.91.7.255/2", // scaler of events // scaler of events
      "7/0.0.99.98.0.255/2", // long
      "7/0.0.99.98.1.255/2", // longer
      "7/0.0.99.98.2.255/2", // longest
      "7/0.0.99.98.3.255/2", // out of this world
      "7/0.0.99.98.4.255/2", // hyperdrive! Keep this in separate tab
    ]
  }

  associationAttrs() {
    return [
      "15/0.0.40.0.1.255/2", // block error; assosication
      "15/0.0.40.0.2.255/2", // assosication1
      "15/0.0.40.0.3.255/2", // assosciation2
    ]
  }

  object_listAttrs() {
    return [
      "2/0.0.40.0.0.255"
    ]
  }


  configureAttrs() {
    return [
      "1/0.128.128.0.0.255/2",
      "1/0.128.128.0.1.255/2",
      "1/0.128.128.0.2.255/2",
      "1/0.128.128.0.3.255/2",
      "1/0.128.128.0.4.255/2",
      "1/0.128.128.0.5.255/2",
      "1/0.128.128.0.6.255/2",
      "1/0.128.128.0.7.255/2",
      "70/0.0.96.3.10.255/5",
      "70/0.0.96.3.10.255/6"
    ]
  }



  readWorkerArr(codes) {
    let
      {nid, device} = this.props.params,
      ref = nid + "/" + device

    codes = _.map(codes, function(code) {
      let [iface, obis, attr] = code.split("/")
      return undefined === attr
         ? [parseInt(iface), obis]
         : [parseInt(iface), obis, parseInt(attr)]
    })

    ConnActions.readWorker(ref, codes )
  }

  readWorker(code) {
    let
      {nid, device} = this.props.params,
      ref = nid + "/" + device,
      [iface, obis, attr] = code.split("/")

    ConnActions.readWorker(ref, [ [parseInt(iface), obis, parseInt(attr)] ] )
  }

  writeWorker(code, value) {
    let
      {nid, device} = this.props.params,
      ref = nid + "/" + device,
      [iface, obis, attr] = code.split("/")

    ConnActions.writeWorker(ref, [ [parseInt(iface), obis, parseInt(attr), (fmt[code] || [])[2], value] ] )
  }


  readObjectList() {
    let
      {nid, device} = this.props.params,
      ref = nid + "/" + device

    ConnActions.readObjectList(ref)
  }

  generateCsv(ref) {
    let data = "name;code;value;raw\r\n";


    data += _.map(DataStore.attrs(ref), (row, k) => {
      let raw = _.isString(row[0]) ? row[0] : b64th(row[0].raw)

      return `${fmt[k][0]};${k};${maybeFmt(k, row[0].value, fmt[k][1])};${raw}`
    }).join('\r\n')

    return data
  }

  saveCsv(ref) {
    let
      blob = new Blob([this.generateCsv(ref)], {type: 'text/csv'}),
      url  = window.URL.createObjectURL(blob),
      a = document.createElement('a')

    a.style = 'display: none'
    a.href = url
    a.download = 'dlms-data.csv'
    a.click();
    window.URL.revokeObjectURL(url)
  }


  render() {
    let
      {nid, device} = this.props.params,
      {loadingReqs, tab, pointers, connState} = this.state,
      ref = nid + "/" + device,
      attrs = this[tab + 'Attrs']()

    const loading = code => _.some(loadingReqs, m => -1 !== _.indexOf(m, code))
    const reading = code => _.some(pointers, m => code === m)

    return (
      <div>
         <Row>
            <Col xs={12} sm={2}>
               <h1>Device</h1>
            </Col>
            <Col xs={12} sm={8} style={{paddingTop: '25px'}}>
               <Navigation {...this.props} />
            </Col>
         </Row>

         <Row>
            <Col xs={12} sm={2}>
               <h4>Send command</h4>

               <ListGroup>
                  <ListGroupItem className={tab == 'object_list' ? 'active' : ''} onClick={() => this.setState({tab: 'object_list'})}>Object List</ListGroupItem>
                  <ListGroupItem className={tab == 'main' ? 'active' : ''} onClick={() => this.setState({tab: 'main'})}>Read All</ListGroupItem>
                  <ListGroupItem className={tab == 'name' ? 'active' : ''} onClick={() => this.setState({tab: 'name'})}>Read name plate</ListGroupItem>
                  <ListGroupItem className={tab == 'daily' ? 'active' : ''} onClick={() => this.setState({tab: 'daily'})}>Daily Load</ListGroupItem>
                  <ListGroupItem className={tab == 'block' ? 'active' : ''} onClick={() => this.setState({tab: 'block'})}>Block Load</ListGroupItem>
                  <ListGroupItem className={tab == 'billing' ? 'active' : ''} onClick={() => this.setState({tab: 'billing'})}>Billing</ListGroupItem>
                  <ListGroupItem className={tab == 'billing_profile' ? 'active' : ''} onClick={() => this.setState({tab: 'billing_profile'})}>Billing Profile</ListGroupItem>
                  <ListGroupItem className={tab == 'events' ? 'active' : ''} onClick={() => this.setState({tab: 'events'})}>Events</ListGroupItem>
                  <ListGroupItem className={tab == 'associations' ? 'active' : ''} onClick={() => this.setState({tab: 'association'})}>Associations</ListGroupItem>
                  <ListGroupItem className={tab == 'configure' ? 'active' : ''} onClick={() => this.setState({tab: 'configure'})}>Configure Meter</ListGroupItem>
               </ListGroup>

               {'object_list' !== tab && <Button onClick={() => this.readWorkerArr(attrs)} bsStyle="primary">Request Data from Meter</Button>}
               {'object_list' === tab && <Button onClick={() => this.readObjectList()} bsStyle="primary">Load Object List</Button>}
               <br />

               {_.map(loadingReqs, (req, k) =>
                  _.size(req) > 0 && <span key={k}>
                     <b>Loading:</b> <em>{k}</em> - <i>({_.size(req)} left)</i><br/>
                  </span>
               )}
               <br />

               <Button onClick={() => this.saveCsv(ref)}>Save as CSV/Excel</Button>

            </Col>
            <Col xs={12} sm={10} style={{paddingTop: '38px'}}>

               <ConnState state={connState} />

               {'configure' !== tab && <OutputTable
                  tab={tab}
                  device={ref}
                  attrs={attrs}
                  reading={reading}
                  loading={loading}
                  readWorker={this.readWorker.bind(this)}
                  readWorkerArr={this.readWorkerArr.bind(this)}
                  readObjectList={this.readObjectList.bind(this)} />}

               {'configure' === tab && <ConfigureView
                  tab={tab}
                  device={ref}
                  attrs={attrs}
                  reading={reading}
                  loading={loading}
                  readWorker={this.readWorker.bind(this)}
                  writeWorker={this.writeWorker.bind(this)}
                  readWorkerArr={this.readWorkerArr.bind(this)}
                  readObjectList={this.readObjectList.bind(this)} />}
            </Col>
         </Row>
      </div>
      )
  }
}

class ConnState extends React.Component {
   constructor(p) {
      super(p)

      this.state = {
         timer: null,
         expired: false
      }
   }

   componentWillReceiveProps(nextProps) {
      let
         {timer} = this.state,
         connState = this.connState(this.props.state)

      if (!timer)
         this.setState({expired: false,
                        timer: setTimeout(() => this.setState({expired: true,
                                                               timer: null}), 7500)})
   }

   connState(connState) {
      let [a, b] = connState || []

      if ('open' === a && 'closed' === b)
         return 'reconnected'
      else if ('closed' === a && 'open' === b)
         return 'disconnected'
      else if ('open' === a)
         return 'connected'
      else
         return 'undefined'
   }

   render() {
      let
         connState = this.connState(this.props.state),
         {timer, expired} = this.state

      switch (connState) {
         case 'reconnected':
            return !expired && <Alert bsStyle="success">
               <Glyphicon glyph="repeat" />&nbsp; Reconnected to backend
            </Alert>

         case 'disconnected':
            return !expired && <Alert bsStyle="warning">
               <Glyphicon glyph="remove" />&nbsp; Backend disconnected.... Try refreshing the page
            </Alert>

         case 'connected':
            return null
            //return !expired && <Alert bsStyle="success">
            //   <Glyphicon glyph="ok" />&nbsp; Backend connection established
            //</Alert>

         case 'connecting':
            return !expired && <Alert bsStyle="warning">
               <Glyphicon glyph="warning-sign" />&nbsp; Connecting to backend
            </Alert>

         case 'undefined':
            return !expired && <Alert bsStyle="warning">
               <Glyphicon glyph="warning-sign" />&nbsp; Waiting for connection to backend....
            </Alert>
      }
   }
}


const fmtOrDummy = function(ref, code) {
   let val = (DataStore.attr(ref, code) || [])[0]
   if (val)
      return _.isString(val) ? val : maybeFmt(code, val.value, (fmt[code] || {})[1])
   else
      return <span style={{width: 101 + "px"}} className="dummy-block">&nbsp;</span>
}

const rawOrDummy = function(ref, code) {
   let val = (DataStore.attr(ref, code) || [])[0]
   if (val)
      return _.isString(val) ? val : b64th(val.raw)
   else
      return <span style={{width: 197  + "px"}} className="dummy-block">&nbsp;</span>
}

class Value extends React.Component {
   render() {
      let {type, value, onChange} = this.props

      switch (type) {
         case 'int':
            return <FormControl
                     onChange={onChange}
                     value={value || ""}
                     placeholder="int" />

         case 'float':
            return <FormControl
                     onChange={onChange}
                     value={value || ""}
                     placeholder="float" />

         case 'bit':
            return <FormControl
                        onChange={onChange}
                        value={value || ""}
                        componentClass="select"
                        placeholder="...">

                     <option value="0">0</option>
                     <option value="1">1</option>
                   </FormControl>

         case 'bool':
            return <FormControl
                        onChange={onChange}
                        componentClass="select"
                        value={value || ""}
                        placeholder="...">

                     <option value="true">true</option>
                     <option value="false">false</option>
                   </FormControl>

         case 'exec':
            return <span>-</span>
      }
   }
}

class ConfigureView extends React.Component {
   constructor(p) {
      super(p)

      this.state = {}
   }

   render() {
      let
         {attrs, reading, loading, tab, readWorker, writeWorker} = this.props,
         ref = this.props.device

      console.log(this.state)

      const unchanged = code => undefined === this.state[code] || this.state[code] == ((DataStore.attr(ref, code) || [])[0] || {}).value

      return (
         <div className="configure-list">
            <table className="table table-striped">
               <thead>
                  <tr>
                     <th>Name</th>
                     <th>LN</th>
                     <th>Current Value</th>
                     <th>Input Value</th>
                     <th>#</th>
                  </tr>
               </thead>
               <tbody>
                  {_.map(attrs, (code, k) =>
                     <tr key={k} className={(reading(code) ? 'reading ' : '') + (loading(code) ? 'loading' : '')}>
                        <td>{(fmt[code] || [])[0] || code}</td>
                        <td>{code}</td>
                        <td>{fmtOrDummy(ref, (fmt[code] || [])[3] || code)}</td>
                        <td>
                           <Value
                              type={(fmt[code] || [])[2]}
                              value={this.state[code] || ((DataStore.attr(ref, code) || [])[0] || {}).value}
                              onChange={ev => {let x = {}; x[code] = ev.target.value; this.setState(x)}}
                              />
                        </td>
                        <td>
                           <Button
                              onClick={() => writeWorker(code, this.state[code])}
                              bsStyle='primary'
                              disabled={(fmt[code] || [])[2] !== 'exec' && (loading(code) || unchanged(code))}>
                              {loading(code) ? 'Loading ...' : ((fmt[code] || [])[2] === 'exec' ? 'Execute' : 'Update Value')}
                           </Button>
                           &nbsp;
                           <Button
                              onClick={() => readWorker((fmt[code] || [])[3] || code)}
                              disabled={loading((fmt[code] || [])[3] || code)}>
                              {loading(code) ? 'Loading ...' : 'Fetch data'}
                           </Button>
                        </td>
                     </tr>
                  )}
               </tbody>
            </table>
         </div>
      )
   }
}

class OutputTable extends React.Component {

   render() {
      let
         {attrs, reading, loading, tab, readWorker, readObjectList, readWorkerArr} = this.props,
         ref = this.props.device

      return (
         <div className="attr-list">
            <table className="table table-striped">
               <thead>
                  <tr>
                     <th>Name</th>
                     <th>LN</th>
                     <th>Value</th>
                     <th>Raw</th>
                     <th>#</th>
                  </tr>
               </thead>
               <tbody>
                  {_.map(attrs, (code, k) =>
                     <tr key={k} className={(reading(code) ? 'reading ' : '') + (loading(code) ? 'loading' : '')}>
                        <td>{(fmt[code] || [])[0] || code}</td>
                        <td>{code}</td>
                        <td>{fmtOrDummy(ref, code)}</td>
                        <td>{rawOrDummy(ref, code)}</td>
                        <td>
                           <Button
                              onClick={() => readWorker(code)}
                              disabled={loading(code)}>
                              {loading(code) ? 'Loading ...' : 'Reload'}
                           </Button>
                        </td>
                     </tr>
                  )}
               </tbody>
            </table>

            {'object_list' !== tab && <Button onClick={() => readWorkerArr(attrs)} bsStyle="primary">Request Data from Meter</Button>}
            {'object_list' === tab && <Button onClick={() => readObjectList()} bsStyle="primary">Load data</Button>}

            <Button onClick={() => this.saveCsv(ref)}>Save as CSV/Excel</Button>
         </div>
      )
   }
}


Device.title = "Device"
Device.path = "/device/:nid/:device"
Device.before = Network

//                     {_.map(DataStore.attrs(ref), (row, k) =>
//                        <tr key={k}>
//                           <td>{fmt[k][0] || k}</td>
//                           <td>{k}</td>
//                           <td>{_.isString(row[0]) ? row[0] : maybeFmt(k, row[0].value, fmt[k][1])}</td>
//                           <td>{_.isString(row[0]) ? row[0] : b64th(row[0].raw)}</td>
//                           <td><Button onClick={() => this.readWorker(k)}>Reload</Button></td>
//                        </tr>
//                     )}
