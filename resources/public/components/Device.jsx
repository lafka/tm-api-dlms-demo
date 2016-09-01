import React from 'react'

import {Row, Col, ListGroup, ListGroupItem, Button, Input, FormControl} from 'react-bootstrap'
import {Alert, Glyphicon} from 'react-bootstrap'
import {LinkContainer} from 'react-router-bootstrap'
import Navigation from './Navigation.jsx'
import {Base64Binary} from '../Base64.js'

import {groups, attrs} from './Device.attrs.js'
import AttributeList from './AttributeList.jsx'

import {NetworkState, Network} from './Network.jsx'

import WorkerQueue from '../state/WorkerQueue.js'
import {DLMSActions} from '../state/DLMSActions.js'

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

      this._mounted = false
      this.state = {
         queue: []
      }

      this.queueAll = this.queueAll.bind(this)
      this.cancelQueue = this.cancelQueue.bind(this)
   }

   componentWillMount() {
      let {nid, device} = this.props.params,
          resource = nid + '/' + device

      this._mounted = true
      WorkerQueue.addChangeListener(this._changeListener = () => {
         let newQueue = WorkerQueue.device(resource)
         if (this._mounted && !_.isEqual(this.state.queue, newQueue))
            this.setState({queue: newQueue})
      })

      DLMSActions.queue(nid)
      DLMSActions.attributes(resource)
   }

   componentWillUnmount() {
      this._mounted = true
   }

   queueAll(group) {
    let
      {nid, device} = this.props.params,
      resource = nid + "/" + device
      _.each(groups[group], (attr) => setTimeout(() => DLMSActions.read(resource, attrs[attr], attr), 0))
   }

   cancelQueue() {
      _.each(this.state.queue, (job) => setTimeout(() => DLMSActions.cancelConcrete(job), 0))
   }

  render() {
    let
      {network} = this.props,
      {nid, device} = this.props.params,
      tab = this.props.location.query.tab || 'main',
      ref = nid + "/" + device

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
            <Col xs={12} sm={3}>
               <h4>Send command</h4>

               <ListGroup>
                  <LinkContainer to={{pathname: `/device/${nid}/${device}`, query: {tab: `main`}}} active={tab==='main'}><ListGroupItem>Read All</ListGroupItem></LinkContainer>
                  <LinkContainer to={{pathname: `/device/${nid}/${device}`, query: {tab: `name`}}}           ><ListGroupItem>Read name plate</ListGroupItem></LinkContainer>
                  <LinkContainer to={{pathname: `/device/${nid}/${device}`, query: {tab: `daily`}}}          ><ListGroupItem>Daily Load</ListGroupItem></LinkContainer>
                  <LinkContainer to={{pathname: `/device/${nid}/${device}`, query: {tab: `block`}}}          ><ListGroupItem>Block Load</ListGroupItem></LinkContainer>
                  <LinkContainer to={{pathname: `/device/${nid}/${device}`, query: {tab: `billing`}}}        ><ListGroupItem>Billing</ListGroupItem></LinkContainer>
                  <LinkContainer to={{pathname: `/device/${nid}/${device}`, query: {tab: `billing_profile`}}}><ListGroupItem>Billing Profile</ListGroupItem></LinkContainer>
                  <LinkContainer to={{pathname: `/device/${nid}/${device}`, query: {tab: `events`}}}         ><ListGroupItem>Events</ListGroupItem></LinkContainer>
                  <LinkContainer to={{pathname: `/device/${nid}/${device}`, query: {tab: `associations`}}}   ><ListGroupItem>Associations</ListGroupItem></LinkContainer>
                  <LinkContainer to={{pathname: `/device/${nid}/${device}`, query: {tab: `configure`}}}      ><ListGroupItem>Configure Meter</ListGroupItem></LinkContainer>
               </ListGroup>

               <Button onClick={() => this.queueAll(tab)}>Request all fields</Button>
               <Button onClick={this.cancelQueue}>Cancel all jobs</Button>

               <hr />

               <h4>Jobs</h4>

               {_.map(this.state.queue,
                  (val, i) => <div className="job-item" key={i}>
                     <a onClick={() => DLMSActions.cancelConcrete(val)}>
                        <Glyphicon glyph="remove" style={{fontSize: '0.8em'}}>&nbsp;</Glyphicon>
                        <b>{val[0]}:</b> <em>{val[2]}</em>
                        {val[3] && <span> = {val[3][1]}</span>}
                     </a>
                  </div>)}

               {this.state.queue.length === 0 && <h5>There are no requests in queue</h5>}
            </Col>
            <Col xs={12} sm={9} style={{paddingTop: '38px'}}>
               {network && <NetworkState network={this.props.network} />}

               <AttributeList
                  queue={this.state.queue}
                  resource={nid + '/' + device}
                  attrs={groups[tab]}
                  definition={attrs} />
            </Col>
         </Row>
      </div>
      )
  }
}

Device.title = "Device"
Device.path = "/device/:nid/:device"
Device.before = Network
