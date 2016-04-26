import React from 'react'
import _ from 'lodash'
import {LinkContainer} from 'react-router-bootstrap'
import {Breadcrumb} from 'react-bootstrap'

export default class Navigation extends React.Component {
   replace(path, props) {
      return _.reduce(props, (p, v, k) => p.replace(":" + k, v), path )
   }

   maybeInject(item) {
      let inject

      if (!item.component)
         return

      if (inject = item.component.before)
         return <LinkContainer
                  onlyActiveOnIndex={true}
                  activeClassName="breadcrumb-active"
                  to={this.replace(inject.path || '', this.props.params)}>
                  <Breadcrumb.Item>{inject.title}</Breadcrumb.Item>
                </LinkContainer>
   }

   render() {
      const depth = this.props.routes.length

      return (
         <Breadcrumb>
            {_.filter(this.props.routes, (item) => -1 !== item.component.path)
               .map((item, idx) =>
                [this.maybeInject(item), <LinkContainer
                  key={idx}
                  onlyActiveOnIndex={true}
                  activeClassName="breadcrumb-active"
                  to={this.replace(item.path || '', this.props.params)}>
                  <Breadcrumb.Item>{item.component.title}</Breadcrumb.Item>
                </LinkContainer>]
            )}
          </Breadcrumb>
      )
   }
}
