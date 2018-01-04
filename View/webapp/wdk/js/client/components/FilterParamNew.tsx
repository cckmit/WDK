import { get, isEqual, memoize, pick } from 'lodash';
import React from 'react';

import {
  ActiveFieldSetAction,
  FieldStateUpdatedAction,
  FiltersUpdatedAction,
} from '../params/FilterParamNew/ActionCreators';
import { MemberFieldState, RangeFieldState, State } from '../params/FilterParamNew/State';
import { getLeaves, sortDistribution } from '../params/FilterParamNew/Utils';
import { Props as ParamProps } from '../params/Utils';
import { Field, Filter, MemberFilter } from '../utils/FilterService';
import { FilterParamNew as TFilterParamNew } from '../utils/WdkModel';
import Loading from './Loading';
import _ServerSideAttributeFilter from './ServerSideAttributeFilter';

const ServerSideAttributeFilter: any = _ServerSideAttributeFilter;

type FieldState = State['fieldStates'][string];

type Props = ParamProps<TFilterParamNew, State>

/**
 * FilterParamNew component
 */
export default class FilterParamNew extends React.PureComponent<Props> {
  constructor(props: Props) {
    super(props);
    this._getFiltersFromValue = memoize(this._getFiltersFromValue);
    this._handleActiveFieldChange = this._handleActiveFieldChange.bind(this);
    this._handleFilterChange = this._handleFilterChange.bind(this);
    this._handleMemberSort = this._handleMemberSort.bind(this);
    this._handleMemberSearch = this._handleMemberSearch.bind(this);
    this._handleRangeScaleChange = this._handleRangeScaleChange.bind(this);
  }

  _getFieldMap = memoize((parameter: Props['parameter']) =>
    new Map(parameter.ontology.map(o => [
      o.term,
      parameter.values == null || parameter.values[o.term] == null
        ? o
        : { ...o, values: parameter.values[o.term].join(' ') }
    ] as [string, Field])))

  _countLeaves = memoize((parameter: Props['parameter']) =>
    getLeaves(parameter.ontology).toArray().length);

  _getFiltersFromValue(value: Props['value']) {
    let { filters = [] } = JSON.parse(value);
    return filters as Filter[];
  }

  _handleActiveFieldChange(term: string) {
    this.props.dispatch(ActiveFieldSetAction.create({ ...this.props.ctx, activeField: term }));
  }

  _handleFilterChange(filters: Filter[]) {
    const {
      ctx,
      dispatch,
      onParamValueChange,
      value,
      uiState: { activeOntologyTerm: activeField }
    } = this.props;
    const prevFilters = this._getFiltersFromValue(this.props.value);

    onParamValueChange(JSON.stringify({ filters }));
    dispatch(FiltersUpdatedAction.create({...ctx, prevFilters, filters}));

    if (activeField != null) {
      // Update summary counts for active field if other field filters have been modified
      const prevOtherFilters = prevFilters.filter(f => f.field != activeField);
      const otherFilters = filters.filter(f => f.field !== activeField);
      if (!isEqual(prevOtherFilters, otherFilters)) {
        dispatch(ActiveFieldSetAction.create({ ...ctx, activeField }));
      }
    }
  }

  // FIXME Move sorting into action creator or reducer and retain `groupBySelected` property value
  _handleMemberSort(field: Field, sort: MemberFieldState['sort']) {
    const filters = this._getFiltersFromValue(this.props.value);
    const fieldState = this.props.uiState.fieldStates[field.term] as MemberFieldState
    this.props.dispatch(FieldStateUpdatedAction.create({
      ...this.props.ctx,
      field: field.term,
      fieldState: {
        ...fieldState,
        sort,
        ontologyTermSummary: {
          ...fieldState.ontologyTermSummary,
          valueCounts: sortDistribution(fieldState.ontologyTermSummary!.valueCounts, sort, filters.find(f => f.field === field.term) as MemberFilter)
        }
      }
    }));
  }

  _handleMemberSearch(field: Field, searchTerm: string) {
    const fieldState = this.props.uiState.fieldStates[field.term] as MemberFieldState
    this.props.dispatch(FieldStateUpdatedAction.create({
      ...this.props.ctx,
      field: field.term,
      fieldState: { ...fieldState, searchTerm }
    }));
  }

  _handleRangeScaleChange(field: Field, fieldState: RangeFieldState) {
    this.props.dispatch(FieldStateUpdatedAction.create({
      ...this.props.ctx,
      field: field.term,
      fieldState: fieldState
    }));
  }

  render() {
    let { parameter, uiState } = this.props;
    let filters = this._getFiltersFromValue(this.props.value);
    let fields = this._getFieldMap(parameter);
    let activeFieldSummary = (
      uiState.activeOntologyTerm &&
      uiState.fieldStates[uiState.activeOntologyTerm].ontologyTermSummary
    );
    let activeFieldState = uiState.activeOntologyTerm == null ? undefined
      : uiState.fieldStates[uiState.activeOntologyTerm] as FieldState;
    let numLeaves = this._countLeaves(parameter);

    return (
      <div className="filter-param">
        {uiState.errorMessage && <pre style={{color: 'red'}}>{uiState.errorMessage}</pre>}
        {uiState.loading && <Loading/>}
        <ServerSideAttributeFilter
          displayName={parameter.filterDataTypeDisplayName || parameter.displayName}

          activeField={uiState.activeOntologyTerm && fields.get(uiState.activeOntologyTerm)}
          activeFieldSummary={activeFieldSummary}
          activeFieldState={activeFieldState}
          fields={fields}
          filters={filters}
          dataCount={uiState.unfilteredCount}
          filteredDataCount={uiState.filteredCount}

          hideFilterPanel={numLeaves === 1}
          hideFieldPanel={numLeaves === 1}

          onFiltersChange={this._handleFilterChange}
          onActiveFieldChange={this._handleActiveFieldChange}
          onMemberSort={this._handleMemberSort}
          onMemberSearch={this._handleMemberSearch}
          onRangeScaleChange={this._handleRangeScaleChange}
        />
      </div>
    )
  }
}
