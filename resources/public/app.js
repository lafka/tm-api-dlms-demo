import React from 'react'
import ReactDOM from 'react-dom'
import { browserHistory, Router, Route, IndexRoute, Link } from 'react-router'
import {Grid} from 'react-bootstrap'

import {Index, Network, Device, NotFound} from './components/'

import Storage from './state/Storage.js'
import StorageActions from './state/StorageActions.js'
import {WebSocketActions, WebSocketConn} from './state/ws.js'
import {DLMSActions} from './state/DLMSActions.js'


class App extends React.Component {
   constructor() {
      super()

      this.state = {
         networks: [],      // all networks
         subscriptions: [], // list of active subscriptions
         backend: null
      }
   }

   componentWillMount() {
      this._mounted = true

      WebSocketActions.open()
         .then( (conn) => {
            StorageActions.networks()
            StorageActions.remoteRefresh()

            this.setState({backend: conn})
         })

      Storage.addChangeListener( this._changeListener = () => {
         if (!_.isEqual( this.state.networks, Storage.networks)) {
            this.setState({ networks: _.map(Storage.networks, _.clone)})
            _.each(Storage.networks, (net) => DLMSActions.queue(net.key))
         }
      })

      WebSocketConn.addChangeListener(this._wsListener = () => {
         if (!this._mounted)
            return

         this.setState({backend: WebSocketConn.conn})
         StorageActions.networks()
         StorageActions.remoteRefresh()
      })


      setInterval(() => WebSocketConn.closed() && StorageActions.remoteRefresh(), 45000)
   }

   componentWillUnmount() {
      this._mounted = false

      Storage.removeChangeListener( this._changeListener )
      WebSocketConn.removeChangeListener( this._wsListener )
   }

   render() {
      console.log('render: app')
      let {networks, subscriptions} = this.state

      if (null === this.state.backend || WebSocketConn.closed())
         return (<Grid><h2>Waiting for backend connection</h2></Grid>)

      return (
         <Grid fluid={true}>
            {React.cloneElement(this.props.children, {
               networks: networks,
               subscriptions: subscriptions,
               network: Storage.network(this.props.params.nid || ""),
            })}
         </Grid>
      )
   }
}

App.title = "Home"
App.path = "/"

ReactDOM.render((
   <Router history={browserHistory}>
      <Route path="/" component={App}>
         <IndexRoute component={Index} />
         <Route path="network/:nid" component={Network} />
         <Route path="device/:nid/:device" component={Device} />
         <Route path="*" component={NotFound} />
      </Route>
   </Router>
), document.getElementById('app'));
