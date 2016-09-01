import React from 'react'

import {Row, Col, ListGroup, ListGroupItem} from 'react-bootstrap'
import {Alert, Glyphicon} from 'react-bootstrap'
import {LinkContainer} from 'react-router-bootstrap'
import Navigation from './Navigation.jsx'

import StorageActions from '../state/StorageActions.js'
import Storage from '../state/Storage.js'

export default class Network extends React.Component {
   constructor() {
      super()

      this.state = {devices: null}
   }

   componentWillMount() {
      this._mounted = true

      Storage.addChangeListener( this._changeListener = () => {
         this.setState({devices: Storage.devices(this.props.params.nid)})
      })


      if (this.props.network) {
         StorageActions.devices(this.props.network.key)
      }
   }

   componentWillReceiveProps(nextProps) {
      if (nextProps.network) {
         StorageActions.devices(nextProps.network.key)
      }
   }

   componentWillUnmount() {
      this._mounted = false
      Storage.removeChangeListener(this._changeListener)
   }

   render() {
      // app feeds the network... no point in having changelisteners here
      let {network} = this.props,
          devices = this.state.devices

      console.log('render: network ' + this.props.params.nid)

      if (!devices)
         return (<div><h2>Loading network....</h2></div>)

      return (
         <div>
            <Row>
               <Col xs={12} sm={4}>
                  <h1>Network: {network.name || network.key}</h1>
               </Col>
               <Col xs={12} sm={8} style={{paddingTop: '25px'}}>
                  <Navigation {...this.props} />
               </Col>
            </Row>

            <Row>

               <NetworkState network={network} />

               <Col xs={12}>
                  <ListGroup>
                     {_.map(devices, (dev , idx) =>
                        <LinkContainer key={idx} to={{pathname: `/device/${dev.network}/${dev.key}`}}>
                           <ListGroupItem className={`type-${dev.type}`}>
                              {dev.type} &ndash; {dev.name || dev.key}
                           </ListGroupItem>
                        </LinkContainer>)}

                     {_.map([0,1,2,3,4,5,6,7,8,9,10].slice(0, 7 - _.size(network.devices)), (idx) =>
                        <ListGroupItem key={idx}>
                           <span style={{width: Math.round(150 + (Math.random() * 100)) + "px"}} className="dummy-block">&nbsp;</span>
                        </ListGroupItem>
                     )}
                  </ListGroup>
               </Col>
            </Row>
         </div>
      )
   }
}

export class NetworkState extends React.Component {
   render() {
      let {network} = this.props

      if (true === network['locked?'])
            return <Alert bsStyle="warning">
               <Glyphicon glyph="warning-sign" />&nbsp; 

               <b>Info:</b> No connection to Gateway, requests will be queued...
            </Alert>

      return null
   }
}


Network.title = "Network"
Network.path = "/network/:nid"
