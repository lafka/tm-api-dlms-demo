import React from 'react'
import {Row, Col, ListGroup, ListGroupItem} from 'react-bootstrap'
import {LinkContainer} from 'react-router-bootstrap'
import _ from 'lodash'

import Navigation from './Navigation.jsx'

export default class Index extends React.Component {
   render() {
    let {networks} = this.props

    console.log('render: index')

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
            <Col xs={12}>
               <ListGroup>
                  {_.map(networks, (net, idx) =>
                     <LinkContainer key={idx} to={{pathname: `/network/${net.key}`}}>
                        <ListGroupItem>{net.name || net.key}</ListGroupItem>
                     </LinkContainer>)}
               </ListGroup>
            </Col>
         </Row>
      </div>
    )
  }
}

Index.path = -1

