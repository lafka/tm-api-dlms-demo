import React from 'react'
import Network from './Network.jsx'

import {Row, Col, ListGroup, ListGroupItem} from 'react-bootstrap'
import Navigation from './Navigation.jsx'

export default class Device extends React.Component {
  constructor() {
      super()

      this.state = {
         lines: []
      }

      this.reqConnect = this.reqConnect.bind(this)
      this.reqAttrs = this.reqAttrs.bind(this)
      this.reqSn = this.reqSn.bind(this)
      this.reqEventLog = this.reqEventLog.bind(this)
      this.reqDisconnect = this.reqDisconnect.bind(this)
  }

  reqConnect(ev) {
    ev.preventDefault()
  }

  reqDisconnect(ev) {
    ev.preventDefault()
  }

  reqAttrs(ev) {
    ev.preventDefault()
  }

  reqSn(ev) {
    ev.preventDefault()
  }

  reqEventLog(ev) {
    ev.preventDefault()
  }

  format(line) {
    return JSON.stringify(line)
  }

  render() {
    console.log('render: device')

    let {lines} = this.state

    return (
      <div>
         <Row>
            <Col xs={12} sm={4}>
               <h1>Device</h1>
            </Col>
            <Col xs={12} sm={8} style={{paddingTop: '25px'}}>
               <Navigation {...this.props} />
            </Col>
         </Row>

         <Row>
            <Col xs={12} sm={4}>
               <h4>Send command</h4>

               <ListGroup>
                  <ListGroupItem onClick={this.reqConnect}>Connect Meter</ListGroupItem>
                  <ListGroupItem onClick={this.reqAttrs}>Get attributes</ListGroupItem>
                  <ListGroupItem onClick={this.reqSn}>Get serial number</ListGroupItem>
                  <ListGroupItem onClick={this.reqEventLog}>Get event log</ListGroupItem>
                  <ListGroupItem onClick={this.reqDisconnect}>Disconnect Meter</ListGroupItem>
               </ListGroup>
            </Col>
         </Row>
         <Row>
            <pre>;; output of DLMS meter data exchange (last 100)


{_.map(_.slice(lines, -100), this.format).join("\n")}
            </pre>
         </Row>
      </div>
      )
  }
}


Device.title = "Device"
Device.path = "/device/:nid/:device"
Device.before = Network
