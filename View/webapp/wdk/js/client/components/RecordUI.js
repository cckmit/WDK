import { Component, PropTypes } from 'react';
import classnames from 'classnames';
import { wrappable } from '../utils/componentUtils';
import Main from './Main';
import Record from './Record';
import RecordNavigationSection from './RecordNavigationSection';
import Sticky from './Sticky';

/**
 * Renders the main UI for the WDK Record page.
 */
class RecordUI extends Component {

  constructor(props) {
    super(props);
    this.state = {
      showAdvanced: false,
      showSidebar: true
    };

    this.toggleCategory = this.toggleCategory.bind(this);
    this.toggleTable = this.toggleTable.bind(this);
    this.toggleSidebar = this.toggleSidebar.bind(this);
    this.showAdvancedCategories = this.showAdvancedCategories.bind(this);
    this.hideAdvancedCategories = this.hideAdvancedCategories.bind(this);
  }

  toggleCategory(category, isCollapsed) {
    this.props.actions.toggleCategoryCollapsed(
      this.props.recordClass.fullName,
      category.name,
      isCollapsed
    );
  }

  toggleTable(table, isCollapsed) {
    this.props.actions.toggleTableCollapsed(
      this.props.recordClass.fullName,
      table.name,
      isCollapsed
    );
  }

  toggleSidebar(event) {
    event.preventDefault();
    this.setState({ showSidebar: !this.state.showSidebar });
  }

  showAdvancedCategories() {
    this.setState({ showAdvanced: true });
  }

  hideAdvancedCategories() {
    this.setState({ showAdvanced: false });
  }

  render() {
    let classNames = classnames({
      'wdk-RecordContainer': true,
      'wdk-RecordContainer__withSidebar': this.state.showSidebar,
      'wdk-RecordContainer__withAdvanced': this.state.showAdvanced
    });

    let sidebarIconClass = classnames({
      'fa fa-lg': true,
      'fa-angle-double-down': !this.state.showSidebar,
      'fa-angle-double-up': this.state.showSidebar
    });

    let categories = this.props.recordClass.attributeCategories.concat(
      { name: undefined, displayName: 'Uncategorized' }
    );

    return (
      <div className={classNames}>
        <Sticky className="wdk-RecordSidebar" fixedClassName="wdk-RecordSidebar__fixed">
          <a href="#" className="wdk-RecordSidebarToggle" onClick={this.toggleSidebar}>
            {this.state.showSidebar ? '' : 'Show Categories '}
            <i className={sidebarIconClass}
              title={this.state.showSidebar ? 'Close sidebar' : 'Open sidebar'}/>
          </a>
          <RecordNavigationSection
            record={this.props.record}
            recordClass={this.props.recordClass}
            onShowAdvanced={this.showAdvancedCategories}
            onHideAdvanced={this.hideAdvancedCategories}
            showAdvanced={this.state.showAdvanced}
            categories={categories}
            collapsedCategories={this.props.collapsedCategories}
            onCategoryToggle={this.toggleCategory}
          />
        </Sticky>
        <Main className="wdk-RecordMain">
          <Record
            record={this.props.record}
            recordClass={this.props.recordClass}
            categories={categories}
            collapsedCategories={this.props.collapsedCategories}
            collapsedTables={this.props.collapsedTables}
            onCategoryToggle={this.toggleCategory}
            onTableToggle={this.toggleTable}
          />
        </Main>
      </div>
    )
  }
}

RecordUI.propTypes = {
  record: PropTypes.object.isRequired,
  recordClass: PropTypes.object.isRequired,
  collapsedCategories: PropTypes.array.isRequired,
  collapsedTables: PropTypes.array.isRequired,
  actions: PropTypes.object.isRequired
};



export default wrappable(RecordUI);