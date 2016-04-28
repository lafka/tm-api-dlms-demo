import React from 'react'
import ReactDOM from 'react-dom'
import { browserHistory, Router, Route, IndexRoute, Link } from 'react-router'
import {Grid} from 'react-bootstrap'

import {Index, Network, Device, NotFound} from './components/'

import {NetworkStore, NetworkActions} from './API.js'


class App extends React.Component {
   constructor() {
      super()

      this.state = {
         networks: -1,
      }
   }

   componentWillMount() {
      this._mounted = true

      if (!localStorage.networks)
         NetworkActions.listNetworks()
      else
         this.setState({networks: JSON.parse(localStorage.networks)})

      NetworkStore.addChangeListener( this._changeListener = () => {
         localStorage.networks = JSON.stringify(NetworkStore.networks)
         this.setState({networks: NetworkStore.networks})
      })
   }

   componentWillUnmount() {
      this._mounted = false

      NetworkStore.removeChangeListener( this._changeListener )
   }

   render() {
      console.log('render: app')
      let {networks} = this.state

      if (-1 === networks)
         return (<Grid><h2>Loading....</h2></Grid>)

      console.log(this.props.params, _.find(networks, {key: this.props.params.nid}))
      return (
         <Grid fluid={true}>
            {React.cloneElement(this.props.children, {
               networks: networks,
               network: _.find(networks, {key: this.props.params.nid})
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
