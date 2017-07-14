import $ from 'jquery';
import { lazy } from '../../utils/componentUtils';
import { getTree } from '../../utils/FilterServiceUtils';
import { Seq } from '../../utils/IterableUtils';
import {
  debounce,
  find,
  includes,
  isEmpty,
  isEqual,
  map,
  noop,
  padStart,
  partial,
  partition,
  property,
  reduce,
  sortBy,
  throttle
} from 'lodash';
import React from 'react';
import PropTypes from 'prop-types';
import { findDOMNode } from 'react-dom';
import Loading from '../Loading';
import Tooltip from '../Tooltip';
import Dialog from '../Dialog';
import CheckboxTree from '../CheckboxTree';

var dateStringRe = /^(\d{4})(?:-(\d{2})(?:-(\d{2}))?)?$/;

/**
 * Returns an strftime style format string.
 * @param {string} dateString
 */
function getFormatFromDateString(dateString) {
  var matches = dateString.match(dateStringRe);
  if (matches !== null) {
    var [ , , m, d ] = matches;
    return  d !== undefined ? '%Y-%m-%d'
          : m !== undefined ? '%Y-%m'
          : '%Y';
  }
}

/**
 * Returns a formatted date.
 *
 * @param {string} format strftime style format string
 * @param {Date} date
 */
function formatDate(format, date) {
  if (!(date instanceof Date)) {
    date = new Date(date);
  }
  return format
  .replace(/%Y/, String(date.getFullYear()))
  .replace(/%m/, padStart(String(date.getMonth() + 1), 2, '0'))
  .replace(/%d/, padStart(String(date.getDate()), 2, '0'));
}

/**
 * @typedef {string[]} StringFilterValue
 */
/**
 * @typedef {{ min: number, max: number }} NumberFilterValue
 */
/**
 * @typedef {Object} Filter
 * @property {string} field
 * @property {StringFilterValue | NumberFilterValue} value
 */

/**
 * @typedef {Object} FilterListProps
 * @property {number} dataCount
 * @property {number} filteredDataCount
 * @property {string?} selectedField
 * @property {function(string): void} onFilterSelect
 * @property {function(Filter): void} onFilterRemove
 * @property {Array<Filter>} filters
 */

/**
 * List of filters configured by the user.
 *
 * Each filter can be used to update the active field
 * or to remove a filter.
 */
class FilterList extends React.Component {

  /**
   * @param {FilterListProps} props
   * @return {React.Component<FilterListProps, void>}
   */
  constructor(props) {
    super(props);
    this.handleFilterSelectClick = this.handleFilterSelectClick.bind(this);
    this.handleFilterRemoveClick = this.handleFilterRemoveClick.bind(this);
  }

  /**
   * @param {Filter} filter
   * @param {Event} event
   */
  handleFilterSelectClick(filter, event) {
    event.preventDefault();
    this.props.onFilterSelect(filter.field);
  }

/**
 * @param {Filter} filter
 * @param {Event} event
 */
  handleFilterRemoveClick(filter, event) {
    event.preventDefault();
    this.props.onFilterRemove(filter);
  }

  render() {
    var { fields, filters, selectedField } = this.props;

    return (
      <div className="filter-items-wrapper">
        {this.props.renderSelectionInfo(this.props)}
        <ul style={{display: 'inline-block', paddingLeft: '.2em'}} className="filter-items">
          {map(filters, filter => {
            var className = selectedField === filter.field ? 'selected' : '';
            var handleSelectClick = partial(this.handleFilterSelectClick, filter);
            var handleRemoveClick = partial(this.handleFilterRemoveClick, filter);
            var display = getFilterDisplay(fields[filter.field], filter.value);

            return (
              <li key={filter.field} className={className}>
                <div className="ui-corner-all">
                  <a className="select"
                    onClick={handleSelectClick}
                    href={'#' + filter.field}
                    title={display}>{display}</a>
                  {/* Use String.fromCharCode to avoid conflicts with
                      character ecoding. Other methods detailed at
                      http://facebook.github.io/react/docs/jsx-gotchas.html#html-entities
                      cause JSX to encode. String.fromCharCode ensures that
                      the encoding is done in the browser */}
                  <span className="remove"
                    onClick={handleRemoveClick}
                    title="remove restriction">{String.fromCharCode(215)}</span>
                </div>
              </li>
            );
          })}
        </ul>
      </div>
    );
  }

}

FilterList.propTypes = {
  onFilterSelect: PropTypes.func.isRequired,
  onFilterRemove: PropTypes.func.isRequired,
  fields: PropTypes.object.isRequired,
  filters: PropTypes.array.isRequired,
  selectedField: PropTypes.string,
  renderSelectionInfo: PropTypes.func
};

FilterList.defaultProps = {
  renderSelectionInfo(props) {
    const { filteredDataCount, dataCount } = props;
    return(
      <span style={{ fontWeight: 'bold', padding: '.6em 0 .8em 0', display: 'inline-block' }}>
        {filteredDataCount} of {dataCount} selected
      </span>
    );
  }
};

/**
 * Renders a Field node.
 */
function FieldListNode({ node, onFieldSelect, isActive }) {
  return node.children.length > 0
    ? (
      <div className="wdk-Link wdk-AttributeFilterFieldParent">{node.field.display}</div>
    ) : (
      <a
        className={'wdk-AttributeFilterFieldItem' +
          (isActive ? ' wdk-AttributeFilterFieldItem__active' : '')}
        href={'#' + node.field.term}
        onClick={e => {
          e.preventDefault();
          onFieldSelect(node.field);
        }}>
        {node.field.display}
      </a>
    );
}

/**
 * Tree of Fields, used to set the active field.
 */
class FieldList extends React.Component {

  constructor(props) {
    super(props);
    this.handleFieldSelect = this.handleFieldSelect.bind(this);
    this.nodeComponent = this.nodeComponent.bind(this);

    this.state = {
      searchTerm: '',

      // expand branch containing selected field
      expandedNodes: this._getPathToField(this.props.fields[this.props.selectedField])
    };
  }

  handleFieldSelect(field) {
    this.props.onFieldSelect(field.term);
    this.setState({ searchTerm: '' });
  }

  nodeComponent({node}) {
    return (
      <FieldListNode
        node={node}
        onFieldSelect={this.handleFieldSelect}
        isActive={this.props.selectedField === node.field.term}
      />
    );
  }

  _getPathToField(field, path = []) {
    if (field == null || field.parent == null) return path;
    return this._getPathToField(this.props.fields[field.parent], path.concat(field.parent))
  }

  render() {
    var { autoFocus, fields } = this.props;

    return (
      <div className="field-list">
        <CheckboxTree
          autoFocusSearchBox={autoFocus}
          tree={getTree(fields)}
          expandedList={this.state.expandedNodes}
          getNodeId={node => node.field.term}
          getNodeChildren={node => node.children}
          onExpansionChange={expandedNodes => this.setState({ expandedNodes })}
          isSelectable={false}
          nodeComponent={this.nodeComponent}
          isSearchable={true}
          searchBoxPlaceholder="Find a quality"
          searchTerm={this.state.searchTerm}
          onSearchTermChange={searchTerm => this.setState({searchTerm})}
          searchPredicate={(node, searchTerms) =>
            searchTerms.every(searchTerm =>
              node.field.display.toLowerCase().includes(searchTerm.toLowerCase()))}
        />
      </div>
    );
  }
}

FieldList.propTypes = {
  autoFocus: PropTypes.bool,
  fields: PropTypes.object.isRequired,
  onFieldSelect: PropTypes.func.isRequired,
  selectedField: PropTypes.string
};


/**
 * Main interactive filtering interface for a particular field.
 */
function FieldFilter(props) {
  let FieldDetail = getFieldDetailComponent(props.field);
  let fieldDetailProps = {
    displayName: props.displayName,
    field: props.field,
    distribution: props.distribution,
    filter: props.filter,
    onChange: props.onChange
  };

  return (
    <div className={'field-detail' + (props.useFullWidth ? ' field-detail__fullWidth' : '')}>
      {!props.field ? <EmptyField displayName={props.displayName}/> : (
        <div>
          <h3>
            {props.field.display + ' '}
            <Tooltip content={FieldDetail.getTooltipContent(fieldDetailProps)}>
              <i className="fa fa-question-circle" style={{ color: 'blue', fontSize: '1rem' }}/>
            </Tooltip>
          </h3>
          <div className="description">{props.field.description}</div>
          {!props.distribution ? <Loading/> : (
            <div>
              <FieldDetail key={props.field.term} {...fieldDetailProps} />
              <div className="filter-param-legend">
                <div>
                  <div className="bar"><div className="fill"></div></div>
                  <div className="label">All {props.displayName}</div>
                </div>
                <div>
                  <div className="bar"><div className="fill filtered"></div></div>
                  <div className="label">{props.displayName} remaining when <em>other</em> criteria have been applied.</div>
                </div>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

FieldFilter.propTypes = {
  displayName: PropTypes.string,
  field: PropTypes.object,
  filter: PropTypes.object,
  distribution: PropTypes.array,
  onChange: PropTypes.func,
  useFullWidth: PropTypes.bool.isRequired
};

FieldFilter.defaultProps = {
  displayName: 'Items'
}


var FilteredData = (function() {

  /**
   * Table of filtered data when filtering on the client side.
   */
  class FilteredData extends React.Component {

    constructor(props) {
      super(props)
      this.openDialog = this.openDialog.bind(this);
      this.handleDialogClose = this.handleDialogClose.bind(this);
      this.handleExpansionClick = setStateFromArgs(this, 'expandedNodes');
      this.handleSelectionChange = setStateFromArgs(this, 'pendingSelectedFields');
      this.handleSearchTermChange = setStateFromArgs(this, 'searchTerm');
      this.handleFieldSubmit = this.handleFieldSubmit.bind(this);
      this.handleSort = this.handleSort.bind(this);
      this.handleHideColumn = this.handleHideColumn.bind(this);
      this.isIgnored = this.isIgnored.bind(this);
      this.getRow = this.getRow.bind(this);
      this.getRowClassName = this.getRowClassName.bind(this);
      this.getCellData = this.getCellData.bind(this);
      this.getPkCellData = this.getPkCellData.bind(this);
      this.renderPk = this.renderPk.bind(this);

      this.state = {
        dialogIsOpen: false,
        pendingSelectedFields: this.props.selectedFields,
        expandedNodes: undefined,
        searchTerm: ''
      };
    }

    componentWillReceiveProps(nextProps) {
      this.setState({
        pendingSelectedFields: nextProps.selectedFields
      });
    }

    openDialog(event) {
      event.preventDefault();
      this.setState({
        dialogIsOpen: true
      });
    }

    handleDialogClose() {
      this.setState({
        dialogIsOpen: false,
        pendingSelectedFields: this.props.selectedFields
      });
    }

    handleFieldSubmit(event) {
      event.preventDefault();
      this.props.onFieldsChange(this.state.pendingSelectedFields);
      this.setState({
        dialogIsOpen: false
      });
    }

    handleSort(term) {
      this.props.onSort(term);
    }

    handleHideColumn(removedField) {
      var nextFields = this.props.selectedFields.filter(field => field != removedField)
      this.props.onFieldsChange(nextFields);
    }

    isIgnored(field) {
      return this.props.ignoredData.indexOf(field) > -1;
    }

    getRow(index) {
      return this.props.filteredData[index];
    }

    getRowClassName(index) {
      return this.isIgnored(this.props.filteredData[index])
        ? 'wdk-AttributeFilter-ItemIgnored'
        : 'wdk-AttributeFilter-Item';
    }

    getCellData(cellDataKey, rowData) {
      return this.props.metadata[cellDataKey][rowData.term].join(', ');
    }

    getPkCellData(cellDataKey, rowData) {
      return {
        datum: rowData,
        isIgnored: this.isIgnored(rowData)
      }
    }

    renderPk(cellData) {
      let { datum, isIgnored } = cellData;
      var handleIgnored = () => {
        // this.props.onIgnored(datum, !isIgnored);
      };
      let checkboxStyle = { visibility: 'hidden' };
      return (
        <label style={{ overflow: 'hidden', whiteSpace: 'nowrap' }}>
          <input
            type="checkbox"
            style={checkboxStyle}
            checked={!isIgnored}
            onChange={handleIgnored}
          />
          {' ' + datum.display + ' '}
        </label>
      );
    }

    render() {
      var { fields, selectedFields, filteredData, displayName, tabWidth, totalSize } = this.props;
      var { dialogIsOpen } = this.state;

      if (!tabWidth) return null;

      return (
        <div className="wdk-AttributeFilter-FilteredData">

          <div className="ui-helper-clearfix" style={{padding: 10}}>
            <div style={{float: 'left'}}>Showing {filteredData.length} of {totalSize} {displayName}</div>
            <div style={{float: 'right'}}>
              <button onClick={this.openDialog}>Add Columns</button>
            </div>
          </div>

          <Dialog
            modal={true}
            open={dialogIsOpen}
            onClose={this.handleDialogClose}
            title="Select Columns"
          >
            <div className="wdk-AttributeFilter-FieldSelector">
              <form ref="fieldSelector" onSubmit={this.handleFieldSubmit}>
                <div style={{textAlign: 'center', padding: 10}}>
                  <button>Update Columns</button>
                </div>
                <CheckboxTree
                  tree={getTree(fields)}
                  getNodeId={node => node.field.term}
                  getNodeChildren={node => node.children}
                  onExpansionChange={this.handleExpansionClick}
                  nodeComponent={({node}) => <span>{node.field.display}</span> }
                  expandedList={this.state.expandedNodes}
                  isSelectable={true}
                  selectedList={this.state.pendingSelectedFields}
                  onSelectionChange={this.handleSelectionChange}
                  searchBoxPlaceholder="Find a quality"
                  searchTerm={this.state.searchTerm}
                  onSearchTermChange={this.handleSearchTermChange}
                  searchPredicate={(node, searchTerms) =>
                    searchTerms.every(searchTerm =>
                      node.field.display.toLowerCase().includes(searchTerm.toLowerCase()))}
                  currentList={selectedFields}
                />
                <div style={{textAlign: 'center', padding: 10}}>
                  <button>Update Columns</button>
                </div>
              </form>
            </div>
          </Dialog>

          <this.props.Table
            width={tabWidth - 10}
            maxHeight={500}
            rowsCount={filteredData.length}
            rowHeight={25}
            rowGetter={this.getRow}
            rowClassNameGetter={this.getRowClassName}
            headerHeight={30}
            onSort={this.handleSort}
            onHideColumn={this.handleHideColumn}
            sortDataKey={this.props.sortTerm}
            sortDirection={this.props.sortDirection}
          >
            <this.props.Column
              label="Name"
              dataKey="__primary_key__"
              fixed={true}
              width={200}
              cellDataGetter={this.getPkCellData}
              cellRenderer={this.renderPk}
              isRemovable={false}
              isSortable={true}
            />
            {selectedFields.map(fieldTerm => {
              const field = fields[fieldTerm];
              return (
                <this.props.Column
                  label={field.display}
                  dataKey={field.term}
                  width={200}
                  cellDataGetter={this.getCellData}
                  isRemovable={true}
                  isSortable={true}
                />
              );
            })}
          </this.props.Table>
        </div>
      );
    }
  }

  FilteredData.propTypes = {
    tabWidth: PropTypes.number,
    totalSize: PropTypes.number.isRequired,
    filteredData: PropTypes.array,
    fields: PropTypes.object,
    selectedFields: PropTypes.array,
    ignoredData: PropTypes.array,
    metadata: PropTypes.object,
    displayName: PropTypes.string,
    onFieldsChange: PropTypes.func,
    onIgnored: PropTypes.func,
    onSort: PropTypes.func,
    sortTerm: PropTypes.string,
    sortDirection: PropTypes.string
  };

  return lazy(render => require(['./Table'], render))(FilteredData)
})();


/**
 * Primary component
 */
export class AttributeFilter extends React.Component {

  constructor(props) {
    super(props);
    this.handleSelectFieldClick = this.handleSelectFieldClick.bind(this);
    this.handleCollapseClick = this.handleCollapseClick.bind(this);
    this.handleExpandClick = this.handleExpandClick.bind(this);
    this.handleFieldsChange = this.handleFieldsChange.bind(this);
    this.handleIgnored = this.handleIgnored.bind(this);
    this.handleSort = this.handleSort.bind(this);
    this.handleFilterRemove = this.handleFilterRemove.bind(this);
    this.handleFieldFilterChange = this.handleFieldFilterChange.bind(this);
    this.shouldAddFilter = this.shouldAddFilter.bind(this);

    this.state = {
      sortTerm: '__primary_key__',
      sortDirection: 'ASC',
      collapsed: false
    };
  }

  componentDidMount() {
    var $node = $(findDOMNode(this));
    $node.find('.filter-param-tabs').tabs({
      activate: (event, ui) => {
        this.setState({
          tabWidth: ui.newPanel.width()
        });
      }
    });
  }

  /**
   * @param {string} field
   * @param {Event} event
   */
  handleSelectFieldClick(field, event) {
    event.preventDefault();
    this.props.onActiveFieldChange(field);
  }

/**
 * @param {Event} event
 */
  handleCollapseClick(event) {
    event.preventDefault();
    this.setState({
      collapsed: true
    });
  }

/**
 * @param {Event} event
 */
  handleExpandClick(event) {
    event.preventDefault();
    this.setState({
      collapsed: false
    });
  }

/**
 * Columns in data table change
 * @param {*} fields
 */
  handleFieldsChange(fields) {
    this.props.onColumnsChange(fields);
  }

  /**
   * @deprecated
   */
  handleIgnored(datum, ignored) {
    let ignoredData = ignored
      ? this.props.ignoredData.concat(datum)
      : this.props.ignoredData.filter(d => d !== datum);
    this.props.onIgnoredDataChange(ignoredData);
  }

/**
 *
 * @param {string} fieldTerm
 */
  handleSort(fieldTerm) {
    let { sortTerm, sortDirection } = this.state;
    let direction = fieldTerm == sortTerm && sortDirection == 'ASC'
      ? 'DESC' : 'ASC';
    this.setState({
      sortTerm: fieldTerm,
      sortDirection: direction
    });
  }

  handleFilterRemove(filter) {
    let filters = this.props.filters.filter(f => f !== filter);
    this.props.onFiltersChange(filters);
  }

  /**
   * @param {Field} field Field term id
   * @param {any} value Filter value
   */
  handleFieldFilterChange(field, value) {
    let filters = this.props.filters.filter(f => f.field !== field.term);
    this.props.onFiltersChange(this.shouldAddFilter(field, value)
      ? filters.concat({ field: field.term, value })
      : filters
    );
  }

  /**
   * @param {string} field Field term id
   * @param {any} value Filter value
   */
  shouldAddFilter(field, value) {
    return field.type === 'string' || field.isRange == false ? value.length !== this.props.activeFieldSummary.length
         : field.type === 'number' ? value.min != null || value.max != null
         : field.type === 'date' ? value.min != null || value.max != null
         : false;
  }

  render() {
    var {
      dataCount,
      filteredData,
      fields,
      columns,
      ignoredData,
      filters,
      invalidFilters,
      activeField,
      activeFieldSummary,
      fieldMetadataMap
    } = this.props;

    let {
      tabWidth,
      sortTerm,
      sortDirection
    } = this.state;

    var displayName = this.props.displayName;
    var selectedFilter = find(filters, filter => {
      return filter.field === activeField;
    });

    var filteredNotIgnored = filteredData.filter(datum => ignoredData.indexOf(datum) === -1);

    var sortedFilteredData = sortBy(filteredData, function(datum) {
      var term = datum.term;
      return sortTerm == '__primary_key__' ? term : fieldMetadataMap[sortTerm][term];
    });

    if (sortDirection == 'DESC') sortedFilteredData.reverse();

    return (
      <div>
        <FilterList
          onFilterSelect={this.props.onActiveFieldChange}
          onFilterRemove={this.handleFilterRemove}
          filters={filters}
          filteredDataCount={filteredNotIgnored.length}
          dataCount={dataCount}
          fields={fields}
          selectedField={activeField}
          renderSelectionInfo={this.props.renderSelectionInfo}
        />

        <InvalidFilterList filters={invalidFilters}/>

        <div className="filter-view">
          <button onClick={this.handleExpandClick}
            style={{
              display: !this.state.collapsed ? 'none' : 'block'
            }} >Select {displayName}</button>

          {/* Tabs */}

          <div className="filter-param-tabs" style={{ display: this.state.collapsed ? 'none' : 'block' }}>
            <ul className="wdk-AttributeFilter-TabNav">
              <li><a href="#filters">Select {displayName}</a></li>
              <li><a href="#data">View selected {displayName} ({filteredData.length})</a></li>
              {this.props.collapsible && (
                <li>
                  <span
                    className="wdk-AttributeFilter-Collapse link"
                    title="Hide selection tool"
                    onClick={this.handleCollapseClick}
                  >Collapse</span>
                </li>
              )}

            </ul>


            {/* Main selection UI */}
            <div id="filters">
              <div className="filters ui-helper-clearfix">
                <FieldList
                  fields={fields}
                  onFieldSelect={this.props.onActiveFieldChange}
                  selectedField={activeField}
                />

                <FieldFilter
                  displayName={displayName}
                  field={this.props.fields[activeField]}
                  filter={selectedFilter}
                  distribution={activeFieldSummary}
                  onChange={this.handleFieldFilterChange}
                />
              </div>
            </div>

            {/* Results table */}

            <div id="data">
              <FilteredData
                tabWidth={tabWidth}
                displayName={displayName}
                onFieldsChange={this.handleFieldsChange}
                onIgnored={this.handleIgnored}
                onSort={this.handleSort}
                sortTerm={sortTerm}
                sortDirection={sortDirection}
                filteredData={sortedFilteredData}
                totalSize={dataCount}
                selectedFields={columns}
                fields={fields}
                ignoredData={ignoredData}
                metadata={fieldMetadataMap}/>
            </div>
          </div>
        </div>
      </div>
    );
  }

}

AttributeFilter.propTypes = {

  displayName: PropTypes.string,
  collapsible: PropTypes.bool,

  // state
  fields: PropTypes.object.isRequired,
  filters: PropTypes.array.isRequired,
  dataCount: PropTypes.number.isRequired,
  filteredData: PropTypes.array.isRequired,
  ignoredData: PropTypes.array.isRequired,
  columns: PropTypes.array.isRequired,
  activeField: PropTypes.string,
  activeFieldSummary: PropTypes.array,
  fieldMetadataMap: PropTypes.object.isRequired,
  renderSelectionInfo: PropTypes.func,

  // not sure if these belong here
  isLoading: PropTypes.bool,
  invalidFilters: PropTypes.array,  // derivable?

  // event handlers
  onActiveFieldChange: PropTypes.func.isRequired,
  onFiltersChange: PropTypes.func.isRequired,
  onColumnsChange: PropTypes.func.isRequired,
  onIgnoredDataChange: PropTypes.func.isRequired

};

AttributeFilter.defaultProps = {
  displayName: 'Items',
  collapsible: true
};

/**
 * Filtering UI for server-side filtering.
 */
export class ServerSideAttributeFilter extends React.Component {

  constructor(props) {
    super(props);
    this.handleSelectFieldClick = this.handleSelectFieldClick.bind(this);
    this.handleFilterRemove = this.handleFilterRemove.bind(this);
    this.handleFieldFilterChange = this.handleFieldFilterChange.bind(this);
    this.shouldAddFilter = this.shouldAddFilter.bind(this);
  }

  handleSelectFieldClick(field, event) {
    event.preventDefault();
    this.props.onActiveFieldChange(field);
  }

  handleFilterRemove(filter) {
    let filters = this.props.filters.filter(f => f !== filter);
    this.props.onFiltersChange(filters);
  }

  /**
   * @param {Field} field Field term id
   * @param {any} value Filter value
   */
  handleFieldFilterChange(field, value) {
    let filters = this.props.filters.filter(f => f.field !== field.term);
    this.props.onFiltersChange(this.shouldAddFilter(field, value)
      ? filters.concat({ field: field.term, value })
      : filters
    );
  }

  /**
   * @param {string} field Field term id
   * @param {any} value Filter value
   */
  shouldAddFilter(field, value) {
    return field.type === 'string' || field.isRange == false ? value.length !== this.props.activeFieldSummary.length
         : field.type === 'number' ? value.min != null || value.max != null
         : field.type === 'date' ? value.min != null || value.max != null
         : false;
  }

  render() {
    var {
      autoFocus,
      hideFilterPanel,
      hideFieldPanel,
      dataCount,
      filteredDataCount,
      fields,
      filters,
      invalidFilters,
      activeField,
      activeFieldSummary
    } = this.props;

    var displayName = this.props.displayName;
    var selectedFilter = find(filters, filter => {
      return filter.field === activeField;
    });

    return (
      <div>
        {hideFilterPanel || (
          <FilterList
            onFilterSelect={this.props.onActiveFieldChange}
            onFilterRemove={this.handleFilterRemove}
            filters={filters}
            fields={fields}
            filteredDataCount={filteredDataCount}
            dataCount={dataCount}
            selectedField={activeField}
            renderSelectionInfo={this.props.renderSelectionInfo}
          />
        )}

        <InvalidFilterList filters={invalidFilters}/>

        {/* Main selection UI */}
        <div className="filters ui-helper-clearfix">
          {hideFieldPanel || (
            <FieldList
              autoFocus={autoFocus}
              fields={fields}
              onFieldSelect={this.props.onActiveFieldChange}
              selectedField={activeField}
            />
          )}

          <FieldFilter
            displayName={displayName}
            field={fields[activeField]}
            filter={selectedFilter}
            distribution={activeFieldSummary}
            onChange={this.handleFieldFilterChange}
            useFullWidth={hideFieldPanel}
          />
        </div>
      </div>
    );
  }

}

ServerSideAttributeFilter.propTypes = {

  displayName: PropTypes.string,
  autoFocus: PropTypes.bool,
  hideFilterPanel: PropTypes.bool,
  hideFieldPanel: PropTypes.bool,

  // state
  fields: PropTypes.object.isRequired, // tree nodes
  filters: PropTypes.array.isRequired,
  dataCount: PropTypes.number,
  filteredDataCount: PropTypes.number,
  activeField: PropTypes.string,
  activeFieldSummary: PropTypes.array,
  renderSelectionInfo: PropTypes.func,

  // not sure if these belong here
  isLoading: PropTypes.bool,
  invalidFilters: PropTypes.array,  // derivable?

  // event handlers
  onActiveFieldChange: PropTypes.func.isRequired,
  onFiltersChange: PropTypes.func.isRequired

};

ServerSideAttributeFilter.defaultProps = {
  displayName: 'Items',
  hideFilterPanel: false,
  hideFieldPanel: false
};

function InvalidFilterList(props) {
  var { filters } = props;

  if (isEmpty(filters)) return null;

  return (
    <div className="invalid-values">
      <p>Some of the options you previously selected are no longer available:</p>
      <ul>
        {map(filters, filter => (
          <li className="invalid">{getFilterDisplay(filter.field, filter.value)}</li>
        ))}
      </ul>
    </div>
  );
}


// Reusable histogram field component. The parent component
// is responsible for preparing the data.

var unwrapXaxisRange = function unwrap(flotRanges) {
  if (flotRanges == null) {
    return { min: null, max: null };
  }

  var { from, to } = flotRanges.xaxis;
  var min = Number(from.toFixed(2));
  var max = Number(to.toFixed(2));
  return { min, max };
};

var distributionEntryPropType = PropTypes.shape({
  value: PropTypes.number.isRequired,
  count: PropTypes.number.isRequired,
  filteredCount: PropTypes.number.isRequired
});

var Histogram = (function() {
  class Histogram extends React.Component {

    constructor(props) {
      super(props);
      this.handleResize = throttle(this.handleResize.bind(this), 100);
      // Set default yAxis max based on distribution
      var yaxisMax = this.computeYAxisMax();
      this.state = { yaxisMax };
    }

    computeYAxisMax() {
      var counts = this.props.distribution.map(entry => entry.count);
      // Reverse sort, then pull out first and second highest values
      var [ max, nextMax ] = counts.sort((a, b) => a < b ? 1 : -1);
      var yaxisMax = max >= nextMax * 2 ? nextMax : max;
      return yaxisMax + yaxisMax * 0.1;
    }

    componentDidMount() {
      $(window).on('resize', this.handleResize);
      $(findDOMNode(this))
        .on('plotselected .chart', this.handlePlotSelected.bind(this))
        .on('plotselecting .chart', this.handlePlotSelecting.bind(this))
        .on('plotunselected .chart', this.handlePlotUnselected.bind(this))
        .on('plothover .chart', this.handlePlotHover.bind(this));

      this.createPlot();
      this.createTooltip();
      this.drawPlotSelection();
    }

    componentWillUnmount() {
      $(window).off('resize', this.handleResize);
    }

    /**
     * Conditionally update plot and selection based on props and state:
     *  1. Call createPlot if distribution changed
     */
    componentDidUpdate(prevProps) {
      if (!isEqual(this.props.distribution, prevProps.distribution)) {
        this.createPlot();
        this.drawPlotSelection();
      }
      if (prevProps.selectedMin !== this.props.selectedMin || prevProps.selectedMax !== this.props.selectedMax) {
        this.drawPlotSelection();
      }
    }

    handleResize() {
      this.plot.resize();
      this.plot.setupGrid();
      this.plot.draw();
      this.drawPlotSelection();
    }

    handlePlotSelected(event, ranges) {
      var range = unwrapXaxisRange(ranges);
      this.props.onSelected(range);
    }

    handlePlotSelecting(event, ranges) {
      if (!ranges) return;
      var range = unwrapXaxisRange(ranges);
      this.props.onSelecting(range);
    }

    handlePlotUnselected() {
      var range = { min: null, max: null };
      this.props.onSelected(range);
    }

    drawPlotSelection() {
      var values = this.props.distribution.map(entry => entry.value);
      var currentSelection = unwrapXaxisRange(this.plot.getSelection());
      var { selectedMin, selectedMax } = this.props;

      // Selection already matches current state
      if (selectedMin === currentSelection.min && selectedMax === currentSelection.max) {
        return;
      }

      if (selectedMin === null && selectedMax === null) {
        this.plot.clearSelection(true);
      } else {
        this.plot.setSelection({
          xaxis: {
            from: selectedMin === null ? Math.min(...values) : selectedMin,
            to: selectedMax === null ? Math.max(...values) : selectedMax
          }
        }, true);
      }
    }

    createPlot() {
      var { distribution, chartType, timeformat } = this.props;

      var values = distribution.map(entry => entry.value);
      var min = Math.min(...values);
      var max = Math.max(...values);

      var barWidth = (max - min) * 0.005;

      var xaxisBaseOptions = chartType === 'date'
        ? { mode: 'time', timeformat: timeformat }
        : {};


      var seriesData = [{
        data: distribution.map(entry => [ entry.value, entry.count ]),
        color: '#AAAAAA'
      },{
        data: distribution.map(entry => [ entry.value, entry.filteredCount ]),
        color: '#DA7272',
        hoverable: false,
        points: { show: true }
      }];

      var plotOptions = {
        series: {
          bars: {
            show: true,
            fillColor: { colors: [{ opacity: 1 }, { opacity: 1 }] },
            barWidth: barWidth,
            lineWidth: 0,
            align: 'center'
          }
        },
        xaxis: Object.assign({
          min: min - barWidth,
          max: max + barWidth,
          tickLength: 0
        }, xaxisBaseOptions),
        yaxis: {
          min: 0,
          max: this.state.yaxisMax
        },
        grid: {
          clickable: true,
          hoverable: true,
          autoHighlight: false,
          borderWidth: 0
        },
        selection: {
          mode: 'x',
          color: '#66A4E7'
        }
      };

      if (this.plot) this.plot.destroy();

      this.$chart = $(findDOMNode(this)).find('.chart');
      this.plot = $.plot(this.$chart, seriesData, plotOptions);
    }

    createTooltip() {
      this.tooltip = this.$chart
        .qtip({
          prerender: true,
          content: ' ',
          position: {
            target: 'mouse',
            viewport: this.$el,
            my: 'bottom center'
          },
          show: false,
          hide: {
            event: false,
            fixed: true
          },
          style: {
            classes: 'qtip-tipsy'
          }
        });
    }

    handlePlotHover(event, pos, item) {
      var qtipApi = this.tooltip.qtip('api'),
        previousPoint;

      if (!item) {
        qtipApi.cache.point = false;
        return qtipApi.hide(item);
      }

      previousPoint = qtipApi.cache.point;

      if (previousPoint !== item.dataIndex) {
        qtipApi.cache.point = item.dataIndex;
        var entry = this.props.distribution[item.dataIndex];
        var formattedValue = this.props.chartType === 'date'
          ? formatDate(this.props.timeformat, entry.value)
          : entry.value;

        // FIXME Format date
        qtipApi.set('content.text',
          this.props.xaxisLabel + ': ' + formattedValue +
          '<br/>Total ' + this.props.yaxisLabel + ': ' + entry.count +
          '<br/>Matching ' + this.props.yaxisLabel + ': ' + entry.filteredCount);
        qtipApi.elements.tooltip.stop(1, 1);
        qtipApi.show(item);
      }
    }

    setYAxisMax(yaxisMax) {
      this.setState({ yaxisMax }, () => {
        this.plot.getOptions().yaxes[0].max = yaxisMax;
        this.plot.setupGrid();
        this.plot.draw();
      });
    }

    render() {
      var { yaxisMax } = this.state;
      var { xaxisLabel, yaxisLabel, distribution } = this.props;

      var counts = distribution.map(entry => entry.count);
      var countsMin = Math.min(...counts);
      var countsMax = Math.max(...counts);

      return (
        <div>
          <div className="chart"></div>
          <div className="chart-title x-axis">{xaxisLabel}</div>
          <div className="chart-title y-axis">
            <div>{yaxisLabel}</div>
            <div>
              <input
                style={{width: '90%'}}
                type="range" min={countsMin + 1} max={countsMax + countsMax * 0.1}
                title={yaxisMax}
                value={yaxisMax}
                onChange={e => this.setYAxisMax(Number(e.target.value))}/>
            </div>
          </div>
        </div>
      );
    }

  }

  Histogram.propTypes = {
    distribution: PropTypes.arrayOf(distributionEntryPropType).isRequired,
    selectedMin: PropTypes.number,
    selectedMax: PropTypes.number,
    chartType: PropTypes.oneOf([ 'number', 'date' ]).isRequired,
    timeformat: PropTypes.string,
    xaxisLabel: PropTypes.string,
    yaxisLabel: PropTypes.string,

    onSelected: PropTypes.func,
    onSelecting: PropTypes.func,
    onUnselected: PropTypes.func
  };

  Histogram.defaultProps = {
    xaxisLabel: 'X-Axis',
    yaxisLabel: 'Y-Axis',
    selectedMin: null,
    selectedMax: null,
    onSelected: noop,
    onSelecting: noop,
    onUnselected: noop
  };

  return lazy(function(render) {
    require(
      [
        'lib/jquery-flot',
        'lib/jquery-flot-categories',
        'lib/jquery-flot-selection',
        'lib/jquery-flot-time'
      ],
      render)
  })(Histogram);
})();

// TODO Add binning
class HistogramField extends React.Component {

  static getTooltipContent(props) {
    return (`
        The graph below shows the distribution of ${props.field.display}
        values. The red bar indicates the number of ${props.displayName}
        that have the ${props.field.display} value and your other selection
        criteria.

        The slider to the left of the graph can be used to scale the Y-axis.
      `);
  }

  constructor(props) {
    super(props);
    this.updateFilter = debounce(this.updateFilter.bind(this), 50);
    this.handleChange = this.handleChange.bind(this)
    this.cacheDistributionOperations(this.props);
  }

  componentWillReceiveProps(nextProps) {
    this.cacheDistributionOperations(nextProps);
  }

  cacheDistributionOperations(props) {
    this.convertedDistribution = props.distribution.map(entry =>
      Object.assign({}, entry, { value: props.toHistogramValue(entry.value)}));
    var values = this.convertedDistribution.map(entry => entry.value);
    var min = props.toFilterValue(Math.min(...values));
    var max = props.toFilterValue(Math.max(...values));
    this.distributionRange = { min, max };
  }

  handleChange() {
    var inputMin = this.refs.min.value
    var inputMax = this.refs.max.value
    var min = inputMin === '' ? null : this.props.toFilterValue(inputMin);
    var max = inputMax === '' ? null : this.props.toFilterValue(inputMax);
    if (!isNaN(min) && !isNaN(max)) this.emitChange({ min, max });
  }

  updateFilter(range) {
    var min = range.min == null ? null : this.props.toFilterValue(range.min);
    var max = range.max == null ? null : this.props.toFilterValue(range.max);
    this.emitChange({ min, max });
  }

  emitChange(range) {
    this.props.onChange(this.props.field, range);
  }

  render() {
    var { field, filter, displayName } = this.props;
    var distMin = this.distributionRange.min;
    var distMax = this.distributionRange.max;

    var { min, max } = filter ? filter.value : {};

    var selectedMin = min == null ? null : this.props.toHistogramValue(min);
    var selectedMax = max == null ? null : this.props.toHistogramValue(max);

    var selectionTotal = filter && filter.selection && filter.selection.length;

    var selection = selectionTotal != null
      ? " (" + selectionTotal + " selected) "
      : null;

    return (
      <div className="range-filter">

        <div className="overview">
          {this.props.overview}
        </div>

        <div>
          {'Between '}
          <input
            ref="min"
            type="text"
            size="6"
            placeholder={distMin}
            value={min || ''}
            onChange={this.handleChange}
          />
          {' and '}
          <input
            ref="max"
            type="text"
            size="6"
            placeholder={distMax}
            value={max || ''}
            onChange={this.handleChange}
          />
          <span className="selection-total">{selection}</span>
        </div>

        <Histogram
          distribution={this.convertedDistribution}
          onSelected={this.updateFilter}
          selectedMin={selectedMin}
          selectedMax={selectedMax}
          chartType={field.type}
          timeformat={this.props.timeformat}
          xaxisLabel={field.display}
          yaxisLabel={displayName}
        />
      </div>
    );
  }

}

HistogramField.propTypes = {
  toFilterValue: PropTypes.func.isRequired,
  toHistogramValue: PropTypes.func.isRequired,
  onChange: PropTypes.func.isRequired,
  field: PropTypes.object.isRequired,
  filter: PropTypes.object,
  overview: PropTypes.node.isRequired,
  displayName: PropTypes.string.isRequired
};


var UNKNOWN_DISPLAY = 'unknown';
var UNKNOWN_VALUE = '@@unknown@@';

class MembershipField extends React.Component {
  static getTooltipContent(props) {
    var displayName = props.displayName;
    var fieldDisplay = props.field.display;
    return (
      `<p>This table shows the distribution of ${displayName} with
        respect to ${fieldDisplay}.</p>

        <p>The <i>Total</i> column indicates the number of
        ${displayName} with the given ${fieldDisplay}
        value.</p>

        <p>The <i>Matching</i> column indicates the number of
        ${displayName} that match the critera chosen for other
        qualities and that have the given ${fieldDisplay}
        value.</p>

        <p>You may add or remove ${displayName} with specific ${fieldDisplay}
        values from your overall selection by checking or unchecking the
        corresponding checkboxes.</p>`);
  }

  constructor(props) {
    super(props);
    this.handleClick = this.handleClick.bind(this);
    this.handleChange = this.handleChange.bind(this);
    this.handleSelectAll = this.handleSelectAll.bind(this);
    this.handleRemoveAll = this.handleRemoveAll.bind(this);
    this.toFilterValue = this.toFilterValue.bind(this);
  }

  toFilterValue(value) {
    return this.props.field.type === 'string' ? String(value)
      : this.props.field.type === 'number' ? Number(value)
      : this.props.field.type === 'date' ? Date(value)
      : value;
  }

  handleClick(event) {
    if (!$(event.target).is('input[type=checkbox]')) {
      var $target = $(event.currentTarget).find('input[type=checkbox]');
      $target.prop('checked', !$target.prop('checked'));
      this.handleChange();
    }
  }

  handleChange() {
    var value = Seq.from(findDOMNode(this).querySelectorAll('input[type=checkbox]:checked'))
      .map(property('value'))
      .map(value => value === UNKNOWN_VALUE ? null : value)
      .map(this.toFilterValue)
      .toArray();
    this.emitChange(value);
  }

  handleSelectAll(event) {
    event.preventDefault();
    var { distribution } = this.props;
    var value = map(distribution, property('value'));
    this.emitChange(value);
  }

  handleRemoveAll(event) {
    event.preventDefault();
    this.emitChange([]);
  }

  emitChange(value) {
    this.props.onChange(this.props.field, value);
  }

  render() {
    var dist = this.props.distribution;
    var total = reduce(dist, (acc, item) => acc + item.count, 0);

    // sort Unkonwn to end of list
    var sortedDistribution = sortBy(this.props.distribution, function({ value }) {
      return value === null ? '\u200b' : value;
    })

    return (
      <div className="membership-filter">

        <div className="membership-wrapper">
          <div className="membership-table-panel">
            <div className="toggle-links">
              <a href="#select-all" onClick={this.handleSelectAll}>select all</a>
              {' | '}
              <a href="#clear-all" onClick={this.handleRemoveAll}>clear all</a>
            </div>
            <table>
              <thead>
                <tr>
                  <th colSpan="2">{this.props.field.display}</th>
                  <th>Total {this.props.displayName}</th>
                  <th>Matching {this.props.displayName}</th>
                  <th>Distribution</th>
                </tr>
              </thead>
              <tbody>
                {map(sortedDistribution, item => {
                  // compute frequency, percentage, filteredPercentage
                  var percentage = (item.count / total) * 100;
                  var disabled = item.filteredCount === 0;
                  var filteredPercentage = (item.filteredCount / total) * 100;
                  var value = this.toFilterValue(item.value) || UNKNOWN_VALUE;
                  var display = item.value || UNKNOWN_DISPLAY;
                  var tooltip = disabled ? `This option does not match any of the other criteria you have selected.` : ``;
                  var isChecked = !this.props.filter || includes(this.props.filter.value, value);
                  var trClassNames = 'member' +
                    (isChecked & !disabled ? ' member__selected' : '') +
                    (disabled ? ' member__disabled' : '');

                  return (
                    <tr key={value} className={trClassNames} onClick={this.handleClick} title={tooltip}>
                      <td><input value={value} type="checkbox" checked={isChecked} onChange={this.handleChange}/></td>
                      <td><span className="value">{display}</span></td>
                      <td><span className="frequency">{item.count}</span></td>
                      <td><span className="frequency">{item.filteredCount}</span></td>
                      <td><div className="bar">
                        <div className="fill" style={{ width: percentage + '%' }}/>
                        <div className="fill filtered" style={{ width: filteredPercentage + '%' }}/>
                      </div></td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    );
  }
}

class NumberField extends React.Component {

  static getTooltipContent(props) {
    return HistogramField.getTooltipContent(props);
  }

  constructor(props) {
    super(props);
    this.toHistogramValue = this.toHistogramValue.bind(this);
    this.toFilterValue = this.toFilterValue.bind(this);
  }

  // FIXME Handle intermediate strings S where Number(S) => NaN
  // E.g., S = '-'
  // A potential solution is to use strings for state and to
  // convert to Number when needed
  parseValue(value) {
    switch (typeof value) {
      case 'string': return Number(value);
      default: return value;
    }
  }

  toHistogramValue(value) {
    return Number(value);
  }

  toFilterValue(value) {
    return value;
  }

  render() {
    var [ knownDist, unknownDist ] = partition(this.props.distribution, function(entry) {
      return entry.value !== null;
    });

    var size = knownDist.reduce(function(sum, entry) {
      return entry.count + sum;
    }, 0);

    var sum = knownDist.reduce(function(sum, entry) {
      return entry.value * entry.count + sum;
    }, 0);

    var values = knownDist.map(entry => entry.value);
    var distMin = Math.min(...values);
    var distMax = Math.max(...values);
    var distAvg = (sum / size).toFixed(2);
    var unknownCount = unknownDist.reduce((sum, entry) => sum + entry.count, 0);
    var overview = (
      <dl className="ui-helper-clearfix">
        <dt>Avg</dt>
        <dd>{distAvg}</dd>
        <dt>Min</dt>
        <dd>{distMin}</dd>
        <dt>Max</dt>
        <dd>{distMax}</dd>
        <dt>Unknown</dt>
        <dd>{unknownCount}</dd>
      </dl>
    );

    return (
      <HistogramField
        {...this.props}
        distribution={knownDist}
        toFilterValue={this.toFilterValue}
        toHistogramValue={this.toHistogramValue}
        overview={overview}
      />
    );
  }

}

class DateField extends React.Component {

  static getTooltipContent(props) {
    return HistogramField.getTooltipContent(props);
  }

  constructor(props) {
    super(props);
    this.toHistogramValue = this.toHistogramValue.bind(this);
    this.toFilterValue = this.toFilterValue.bind(this);
  }

  componentWillMount() {
    this.timeformat = getFormatFromDateString(this.props.distribution[0].value);
  }

  componentWillUpdate(nextProps) {
    this.timeformat = getFormatFromDateString(nextProps.distribution[0].value);
  }

  toHistogramValue(value) {
    return new Date(value).getTime();
  }

  toFilterValue(value) {
    switch (typeof value) {
      case 'number': return formatDate(this.timeformat, value);
      default: return value;
    }
  }

  render() {
    var [ knownDist, unknownDist ] = partition(this.props.distribution, function(entry) {
      return entry.value !== null;
    });


    var values = sortBy(knownDist.map(entry => entry.value), value => new Date(value).getTime());
    var distMin = values[0];
    var distMax = values[values.length - 1];

    var dateDist = knownDist.map(function(entry) {
      // convert value to time in ms
      return Object.assign({}, entry, {
        value: new Date(entry.value).getTime()
      });
    });

    var unknownCount = unknownDist.reduce((sum, entry) => sum + entry.count, 0);

    var overview = (
      <dl className="ui-helper-clearfix">
        <dt>Min</dt>
        <dd>{distMin}</dd>
        <dt>Max</dt>
        <dd>{distMax}</dd>
        <dt>Unknown</dt>
        <dd>{unknownCount}</dd>
      </dl>
    );

    return (
      <HistogramField
        {...this.props}
        timeformat={this.timeformat}
        distribution={dateDist}
        toFilterValue={this.toFilterValue}
        toHistogramValue={this.toHistogramValue}
        overview={overview}
      />
    );
  }

}

function EmptyField(props) {
  return (
    <div>
      <h3>You may reduce the selection of {props.displayName} by
        selecting qualities on the left.</h3>
      <p>For each quality, you can choose specific values to include. By
        default, all values are selected.</p>
    </div>
  );
}

function getFieldDetailComponent(field) {
  return field.isRange == false ? MembershipField
    : field.type == 'string' ? MembershipField
    : field.type == 'number' ? NumberField
    : field.type == 'date' ? DateField
    : null;
}

function getFilterDisplay(field, value) {
  return typeof field === 'string' ? `${field} is ${JSON.stringify(value)}`
    // range filter display
    : field.isRange ? `${field.display} is ` +
      ( value.min == null ? `less than ${value.max}`
      : value.max == null ? `greater than ${value.min}`
      : `between ${value.min} and ${value.max}`)

    // membership filter display
    : ( value.length === 0 ? `No ${field.display} selected`
      : `${field.display} is ${value.map(entry => entry === null ? UNKNOWN_DISPLAY : entry).join(', ')}`);
}

function setStateFromArgs(instance, ...argsNames) {
  const length = argsNames.length;
  return function (...args) {
    if (__DEV__ && length !== args.length) {
      console.error(
        'Unexpected number of arguments received in `setStateFromArgs`.',
        'Expected %d, but got %d',
        length,
        args.length
      );
    }
    const nextState = {};
    for (let i = 0; i < length; i++) {
      nextState[argsNames[i]] = args[i];
    }
    instance.setState(nextState);
  }
}
