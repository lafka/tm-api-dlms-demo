import React from 'react'
import {Row, Col, ListGroup, ListGroupItem} from 'react-bootstrap'
import {LinkContainer} from 'react-router-bootstrap'
import _ from 'lodash'

import Navigation from './Navigation.jsx'

import StorageActions from '../state/StorageActions.js'
import WorkerQueue from '../state/WorkerQueue.js'
import {DLMSActions} from '../state/DLMSActions.js'

export default class Index extends React.Component {
   render() {
    let {networks} = this.props

    console.log('render: index')

    return (
      <div>
         <Row>
            <Col xs={12} sm={4}>
               <h1>Networks</h1>
            </Col>
            <Col xs={12} sm={8} style={{paddingTop: '25px'}}>
               <Navigation {...this.props} />
            </Col>
         </Row>
         <Row>
            <Col xs={12}>
               <ListGroup>
                  {_.map(networks, (net, idx) =>
                     <LinkContainer key={idx} to={{pathname: `/network/${net.key}`}}>
                        <ListGroupItem className={true === net['locked?'] ? 'disconnected' : 'connected'}>
                           {net.name || net.key}

                           &nbsp;&ndash;&nbsp;
                           <em><NetworkState network={net} /></em>
                        </ListGroupItem>
                     </LinkContainer>)}
               </ListGroup>
            </Col>
         </Row>
      </div>
    )
  }
}

export class NetworkState extends React.Component {
   constructor() {
      super()

      this._mounted = false
      this.state = {queue: null}
   }

   componentWillMount() {
      this._mounted = true

      //WorkerQueue.addChangeListener( this._changeListener = () => {
      //   let {network} = this.props
      //   this.setState({queue: WorkerQueue.network(network.key)})
      //})

      //if (this.props.network)
      //   DLMSActions.queue(this.props.network.key)
   }

   componentWillUnmount() {
      this._mounted = false

      //WorkerQueue.removeChangeListener(this._changeListener)
   }


   render() {
      let
         {network} = this.props,
         locked = network['locked?'],
         text = "",
         {queue} = this.state

      if (null === locked)
         text = "Unknown connection state"
      else if (locked)
         text = "Disconnected"
      else
         text = "Connected"

      text += ' — ' + (queue || []).length + ' items in queue'

      return (
         <span>{text}</span>
      )
   }
}

Index.path = -1

