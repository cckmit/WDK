import { loadBasketStatus } from './UserActionCreator';
import {
  getTree,
  nodeHasProperty,
  getPropertyValue
} from '../utils/OntologyUtils';
import {
  preorderSeq
} from '../utils/TreeUtils';

export let actionTypes = {
  SET_ACTIVE_RECORD: 'record/set-active-record',
  SET_ACTIVE_RECORD_LOADING: 'record/set-active-record-loading',
  SET_ERROR: 'record/set-error',
  SHOW_SECTION: 'record/show-section',
  HIDE_SECTION: 'record/hide-section',
  UPDATE_NAVIGATION_QUERY: 'record/update-navigation-query'
};

let isLeafFor = recordClassName => node => {
  return (
    (nodeHasProperty('targetType', 'attribute', node) || nodeHasProperty('targetType', 'table', node))
    && nodeHasProperty('recordClassName', recordClassName, node)
    && (nodeHasProperty('scope', 'record', node) || nodeHasProperty('scope', 'record-internal', node))
    )
}

let isNotInternal = node => {
  return nodeHasProperty('scope', 'record', node);
}

let getAttributes = tree =>
  preorderSeq(tree).filter(node => nodeHasProperty('targetType', 'attribute', node)).toArray()

let getTables = tree =>
  preorderSeq(tree).filter(node => nodeHasProperty('targetType', 'table', node)).toArray()

let getNodeName = node => getPropertyValue('name', node);

/**
 * @param {string} recordClassName
 * @param {Object} primaryKeyValues
 */
export function setActiveRecord(recordClassName, primaryKeyValues) {
  return function run(dispatch, { wdkService }) {
    dispatch({ type: actionTypes.SET_ACTIVE_RECORD_LOADING });

    let details$ = fetchRecordDetails(wdkService, recordClassName, primaryKeyValues);
    let basketAction$ = details$.then(details => dispatch(loadBasketStatus(details.recordClass.name, details.record.id)));

    return Promise.all([ details$, basketAction$ ]).then(([ details ]) => {
      return dispatch({
        type: actionTypes.SET_ACTIVE_RECORD,
        payload: details
      });
    }, error => {
      dispatch({
        type: actionTypes.SET_ERROR,
        payload: { error }
      });
      throw error;
    });
  }
}

/** Update a section's collapsed status */
export function updateSectionCollapsed(recordClassName, sectionName, isCollapsed) {
  return {
    type: isCollapsed ? actionTypes.HIDE_SECTION : actionTypes.SHOW_SECTION,
    payload: {
      recordClass: recordClassName,
      name: sectionName
    }
  };
}

export function updateNavigationQuery(query) {
  return {
    type: actionTypes.UPDATE_NAVIGATION_QUERY,
    payload: { query }
  };
}

/**
 * Helper to fetch record details from Wdk Service.
 * Returns a Promise that resolves to an Object of data needed by View store.
 */
function fetchRecordDetails(wdkService, recordClassUrlSegment, primaryKeyValues) {
  let questions$ = wdkService.getQuestions();
  let recordClasses$ = wdkService.getRecordClasses();
  let recordClass$ = wdkService.findRecordClass(r => r.urlSegment === recordClassUrlSegment);
  let categoryTree$ = Promise.all([ recordClass$, wdkService.getOntology() ])
    .then(([ recordClass, ontology ]) => getTree(ontology, isLeafFor(recordClass.name)));
  let record$ = Promise.all([ recordClass$, categoryTree$ ]).then(([ recordClass, categoryTree ]) => {
    let attributes = getAttributes(categoryTree).map(getNodeName);
    let tables = getTables(categoryTree).map(getNodeName);
    let primaryKey = recordClass.primaryKeyColumnRefs
      .map((ref, index) => ({ name: ref, value: primaryKeyValues[index] }));
    let options = { attributes, tables };
    return wdkService.getRecord(recordClass.name, primaryKey, options)
  });

  return Promise.all([ record$, categoryTree$, recordClass$, recordClasses$, questions$ ])
  .then(([ record, categoryTree, recordClass, recordClasses, questions ]) => {
     let newTree = getTree({tree: categoryTree}, isNotInternal);
     return {
        record, categoryTree: newTree, recordClass, recordClasses, questions
      }
    }
  );
}
