import $ from 'jquery';
import {Component, PropTypes} from 'react';
import {useRouterHistory, Router, Route, IndexRoute} from 'react-router';
import {createHistory} from 'history';
import AppController from './AppController';
import IndexController from './IndexController';
import RecordController from './RecordController';
import NotFoundController from './NotFoundController';
import AnswerController from './AnswerController';
import QuestionListController from './QuestionListController';
import DownloadFormController from './DownloadFormController';
import UserProfileController from './UserProfileController';
import UserPasswordChangeController from './UserPasswordChangeController';
import SiteMapController from './SiteMapController';

let REACT_ROUTER_LINK_CLASSNAME = 'wdk-ReactRouterLink';
let GLOBAL_CLICK_HANDLER_SELECTOR = `a:not(.${REACT_ROUTER_LINK_CLASSNAME})`;
let RELATIVE_LINK_REGEXP = new RegExp('^((' + location.protocol + ')?//)?' + location.host);

/** WDK Application Root */
export default class Root extends Component {

  constructor(props, context) {
    super(props, context);
    this.history = useRouterHistory(createHistory)({ basename: this.props.rootUrl });
    // Used to inject wdk content as props of Route Component
    this.createElement = (RouteComponent, routerProps) => {
      let { makeDispatchAction, stores } = this.props;
      return (
        <RouteComponent {...routerProps} makeDispatchAction={makeDispatchAction} stores={stores}/>
      );
    };
    this.routes = (
      <Route path="/" component={AppController}>
        <IndexRoute component={IndexController}/>
        <Route path="search/:recordClass/:question/result" component={AnswerController}/>
        <Route path="record/:recordClass/download/*" component={DownloadFormController}/>
        <Route path="record/:recordClass/*" component={RecordController}/>
        <Route path="step/:stepId/download" component={DownloadFormController}/>
        <Route path="user/profile" component={UserProfileController}/>
        <Route path="user/profile/password" component={UserPasswordChangeController}/>
        <Route path="data-finder" component={SiteMapController}/>
        <Route path="question-list" component={QuestionListController}/>
        {this.props.applicationRoutes.map(route => ( <Route key={route.path} {...route}/> ))}
        <Route path="*" component={NotFoundController}/>
      </Route>
    );
    this.handleGlobalClick = this.handleGlobalClick.bind(this);
  }

  handleGlobalClick(event) {
    let hasModifiers = event.metaKey || event.altKey || event.shiftKey || event.ctrlKey || event.which !== 1;
    let href = event.currentTarget.getAttribute('href').replace(RELATIVE_LINK_REGEXP, '');
    if (!hasModifiers && href.startsWith(this.props.rootUrl)) {
      this.history.push(href.slice(this.props.rootUrl.length));
      event.preventDefault();
    }
  }

  componentDidMount() {
    /** install global click handler */
    $(document).on('click', GLOBAL_CLICK_HANDLER_SELECTOR, this.handleGlobalClick);
  }

  componentWillUnmount() {
    $(document).off('click', GLOBAL_CLICK_HANDLER_SELECTOR, this.handleGlobalClick);
  }

  render() {
    return (
      <Router history={this.history} createElement={this.createElement} routes={this.routes}/>
    );
  }
}

Root.propTypes = {
  rootUrl: PropTypes.string,
  makeDispatchAction: PropTypes.func.isRequired,
  stores: PropTypes.object.isRequired,
  applicationRoutes: PropTypes.array.isRequired
};

Root.defaultProps = {
  rootUrl: '/'
};
