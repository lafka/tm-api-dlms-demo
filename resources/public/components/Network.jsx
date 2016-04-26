import React from 'react'

import {Row, Col, ListGroup, ListGroupItem} from 'react-bootstrap'
import {LinkContainer} from 'react-router-bootstrap'
import Navigation from './Navigation.jsx'

export default class Network extends React.Component {
   render() {
      // app feeds the network... no point in having changelisteners here
      let {network} = this.props

      console.log('render: network', network, this.props.params.nid)

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
               <Col xs={12}>
                  <ListGroup>
                     {_.map(network.devices, (dev , idx) =>
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


Network.title = "Network"
Network.path = "/network/:nid"
