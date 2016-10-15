import _ from 'lodash';
import {EventEmitter} from 'events';
import {makeTree} from './FilterServiceUtils';

// TODO Integrate with Flux architecture
//
// The likely way to integrate this class will be to define action creators
// that dispatch filterParam change actions, and to make this class a stateless
// service class (possibly with some configuration options passed to the
// constructor). ViewStores would handle handle the dispatched actions.

const CHANGE_EVENT = 'change';

interface BaseField {
  type: string;
  term: string;
  parent?: string;
  leaf?: 'true';
}

export interface StringField extends BaseField {
  type: 'string';
}

export interface NumberField extends BaseField {
  type: 'number';
}

export interface DateField extends BaseField {
  type: 'date';
}

export type Field = StringField | NumberField | DateField;

export type FieldTreeNode = {
  field: Field;
  children: FieldTreeNode[];
}

export type Datum = {
  term: string;
  display: string;
}

export type Distribution = Array<{
  value: string|null;
  count: number;
  filteredCount: number;
}>;

export type Metadata = {
  [datum_term: string]: string[];
}


export type IFilter<Values, Field> = {
  field: Field;
  values: Values;
  display: string;
  selection?: Datum[];
}

export type MemberFilter = IFilter<string[], StringField>;

export type RangeFilter = IFilter<{ min: string; max: string; }, NumberField | DateField>;

export type Filter = MemberFilter | RangeFilter;

export interface FilterServiceAttrs {
  fields: Field[];
  data: Datum[];
  columns: Field[];
  fieldMetadataMap: {
    [field_term: string]: Metadata;
  };
}

/** Abstract service class */
export default class FilterService {

  private _emitter: EventEmitter;
  isLoading: boolean;
  filters: Filter[];
  ignoredData: Datum[];
  fields: FieldTreeNode[];
  selectedField: Field;
  data: Datum[];
  filteredData: Datum[];
  columns: Field[];
  distributionMap: {
    [datum_term: string]: Distribution;
  };
  fieldMetadataMap: {
    [field_term: string]: Metadata;
  };

  constructor(attrs: FilterServiceAttrs) {

    this._emitter = new EventEmitter;

    this.isLoading = false;

    this.filters = [];

    // ignored data
    this.ignoredData = [];

    // metadata properties
    this.fields = makeTree(attrs.fields || []);

    // unfiltered data, used for local filtering
    this.data = attrs.data || [];

    // filtered data
    this.filteredData = this.data;

    // visible columns in results
    this.columns = attrs.columns || [];

    // field distributions keyed by field term
    this.distributionMap = {};

    // map of metadata for each field
    this.fieldMetadataMap = attrs.fieldMetadataMap || {};

    // bind all methods
    _.bindAll(this);
  }

  addListener(listener: () => void) {
    this._emitter.on(CHANGE_EVENT, listener);
    return {
      remove: () => {
        this._emitter.removeListener(CHANGE_EVENT, listener);
      }
    };
  }

  getState() {
    return _.pick(
      this,
      'fields',
      'filters',
      'data',
      'filteredData',
      'ignoredData',
      'columns',
      'selectedField',
      'isLoading',
      'invalidFilters',
      'distributionMap',
      'fieldMetadataMap'
    );
  }

  selectField(field: Field) {
    this.isLoading = true;
    this.selectedField = field;
    this._emitChange();

    this.getFieldDistribution(field)
      .then(distribution => {
        this.distributionMap[field.term] = distribution;
        this.isLoading = false;
        this._emitChange();
      });
  }

  updateFilters(filters: Filter[]) {
    this.filters = filters;
    this.isLoading = true;
    this._emitChange();

    let filter = this.selectedField && this.filters.find(filter => filter.field.term === this.selectedField.term);
    var promises: [
      Promise<Datum[]>,
      Promise<Datum[]|undefined>,
      Promise<Distribution|undefined>
    ] = [
      this.getFilteredData(this.filters),
      filter ? this.getFilteredData([filter]) : Promise.resolve(),
      this.selectedField ? this.getFieldDistribution(this.selectedField) : Promise.resolve()
    ];

    Promise.all(promises).then(([ filteredData, filterSelection, distribution ]) => {
      if (distribution) {
        this.distributionMap[this.selectedField.term] = distribution;
      }
      if (filter) {
        filter.selection = filterSelection;
      }
      this.filteredData = filteredData;
      this.isLoading = false;
      this._emitChange();
    });
  }

  updateColumns(fields: Field[]) {
    this.isLoading = true;
    this._emitChange();

    Promise.all(fields.map(field => this.getFieldMetadata(field)))
      .then(() => {
        this.columns = fields;
        this.isLoading = false;
        this._emitChange();
      });
  }

  updateIgnoredData(data: Datum[]) {
    this.ignoredData = data;
    this._emitChange();
  }

  // Methods to override
  // ------------------

  // Returns a Promise-like that resolves with the field's distribution.
  //
  //     [ { value, count, filteredCount } ]
  //
  getFieldDistribution(field: Field): Promise<Distribution> {
    throw new Error('getFieldDistribution() should be implemented ' + field);
  }

  // Returns a Promise-like that resolves with the filtered data.
  //
  //     [ { term, display } ]
  //
  getFilteredData(filters: Filter[]): Promise<Datum[]> {
    throw new Error('getFilteredData() should be implemented ' + filters);
  }

  // Returns a Promise-like that resolves with the field's metadata:
  //
  //     [ { data_term: field_value } ]
  //
  getFieldMetadata(field: Field): Promise<Metadata> {
    throw new Error('getFieldMetadata() should be implemented ' + field);
  }

  _emitChange(): void {
    this._emitter.emit(CHANGE_EVENT);
  }

}