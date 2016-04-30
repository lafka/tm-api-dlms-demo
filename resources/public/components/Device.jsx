import React from 'react'
import Network from './Network.jsx'

import {Row, Col, ListGroup, ListGroupItem} from 'react-bootstrap'
import Navigation from './Navigation.jsx'
import {ConnActions, ConnStore} from '../API.js'
import {Base64Binary} from '../Base64.js'

export default class Device extends React.Component {
  constructor() {
      super()

      this.state = {
         lines: [],
         err: null
      }


      this._mounted = false

      this.reqConnect = this.reqConnect.bind(this)
      this.reqAttrs = this.reqAttrs.bind(this)
      this.reqInfo = this.reqInfo.bind(this)
      this.reqEventLog = this.reqEventLog.bind(this)
      this.reqDisconnect = this.reqDisconnect.bind(this)
  }

  componentWillMount() {
    let {nid, device} = this.props.params,
        ref = [nid,device].join("/")
    this._mounted = true

    ConnActions.open(ref)
     .then( (conn) => this.setState({err: null}) )
     .catch( (err) => this.setState({err}) )

      ConnStore.addChangeListener(this._changeListener = (ev) => {
         this.setState({lines: ConnStore.data(ref)})
      })
  }

  componentWillUnmount() {
    this._mounted = false
    ConnStore.removeChangeListener(this._changeListener)
  }

  reqConnect(ev) {
    ev.preventDefault()
    let {nid, device} = this.props.params

    ConnActions.sendConnect(nid + "/" + device)
  }

  reqDisconnect(ev) {
    ev.preventDefault()
    let {nid, device} = this.props.params

    ConnActions.sendDisconnect(nid + "/" + device)
  }

  reqAttrs(ev) {
    ev.preventDefault()
    let {nid, device} = this.props.params

    ConnActions.getAttrs(nid + "/" + device)
  }

  reqInfo(ev) {
    ev.preventDefault()
    let {nid, device} = this.props.params

    ConnActions.getInfo(nid + "/" + device)
  }

  reqEventLog(ev) {
    ev.preventDefault()
    let {nid, device} = this.props.params

    ConnActions.getEventLog(nid + "/" + device)
  }

  format(line) {
    let
      {origin, ev} = line,
      rest = _.omit(line, ['origin', 'ev'])

    const b64tohex = (buf) =>
               Base64Binary.decode(buf)
                           .map( (c) => ("0" + c.toString(16)).slice(-2) )
                           .join(' ')

    if ('meta' === ev)
      return

    if (rest.hdlc) {
      let frame = rest.hdlc
      return origin + "[" + ev + "] (hdlc) -> "
         + frame.type
         + ': src: '       + frame['src-addr']
         + ', dest: '      + frame['dest-addr']
         + ', send-seq: '  + frame['send-seq']
         + ', recv-seq: '  + frame['recv-seq']
         + ', segmented: ' + frame['segmented']
         + ', info: '      + (frame['information-field'] ? b64tohex(frame['information-field']) : '(none)')
    } else if (origin === 'client' && ev === 'connected') {
      return origin + "[" + ev + "]";
    } else if (ev === 'error') {
      return origin + "[" + ev + "] -> " + rest.message;
    } else if (rest['get-attributes']) {
      const fmtattrs = function(acc, input) {
        let classId = input['class-id'],
            {medium, obis, attributes, methods} = input,
            strattrs = "",
            strmethods = ""

        strattrs = _.map(attributes, (a) =>
            "\t\t"
               + a['attribute-addr']
               + ' : ' + a['id']
               + ' @ ' + a['access-mode']
               + ' / ' + a['selective-access'].join(', ')
         ).join('\n')

        strmethods = _.map(methods, (a) =>
            "\t\t"
               + a['method-addr']
               + ' : ' + a['id']
               + ' @ ' + a['access-mode']
         ).join('\n')

        return acc
            + '\n' + medium
            + " / " + classId
            + " @ " + obis
            + "\n\tAttributes:\n" + strattrs
            + "\n\tMethods:\n" + strmethods
      }

      return 'get-attributes:\n' + _.reduce(rest['get-attributes'], fmtattrs, "")
    } else if (rest['get-info']) {
      return 'get-info:\n'
         + '\t' + _.map(rest['get-info'], (v, k) => k + ":\t" + v).join('\n\t')
    }

    return origin + "[" + ev + "] -> " + JSON.stringify(rest)
  }

  render() {
    console.log('render: device')

    let {lines} = this.state

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
                  <ListGroupItem onClick={this.reqConnect}>Connect Meter</ListGroupItem>
                  <ListGroupItem onClick={this.reqAttrs}>Get attributes</ListGroupItem>
                  <ListGroupItem onClick={this.reqInfo}>Get device info</ListGroupItem>
                  <ListGroupItem onClick={this.reqEventLog}>Get event log</ListGroupItem>
                  <ListGroupItem onClick={this.reqDisconnect}>Disconnect Meter</ListGroupItem>
               </ListGroup>
            </Col>
            <Col xs={12} sm={10} style={{paddingTop: '38px'}}>
               <pre style={{height: '400px'}}>;; output of DLMS meter data exchange (last 100)<br /><br /> <br />


{_.filter(_.map(lines, this.format), (n) => null !== n).join("\n")}
               </pre>
            </Col>
         </Row>
      </div>
      )
  }
}


Device.title = "Device"
Device.path = "/device/:nid/:device"
Device.before = Network
