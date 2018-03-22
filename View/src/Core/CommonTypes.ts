import { ComponentType } from 'react';

import AbstractViewController from 'Core/Controllers/AbstractViewController';
import WdkDispatcher from 'Core/State/Dispatcher';
import GlobalDataStore from 'Core/State/Stores/GlobalDataStore';
import WdkStore from 'Core/State/Stores/WdkStore';
import { Action, ActionCreatorResult, ActionCreatorServices } from 'Utils/ActionCreatorUtils';
import { UserDataset } from 'Utils/WdkModel';


export interface StoreConstructor<T extends WdkStore> {
  new(dispatcher: WdkDispatcher<Action>, channel: string, globalDataStore: GlobalDataStore, services: ActionCreatorServices): T;
}

export interface DispatchAction {
  (action: ActionCreatorResult<Action>): any;
}

export interface MakeDispatchAction {
  (channel: string): DispatchAction
}

export interface Constructor<T> {
  new(...args: any[]): T;
}

export interface Container<T> {
  get(Class: Constructor<T>): T;
}

export interface ViewControllerProps<Store> {
  stores: Container<Store>;
  makeDispatchAction: MakeDispatchAction;
}

export type AbstractViewControllerClass = typeof AbstractViewController;

export interface RouteSpec {
  path: string;
  component: ComponentType<ViewControllerProps<WdkStore>>
}

export interface MesaColumn {
  key: string;
  name?: string;
  type?: string;
  sortable?: boolean;
  filterable?: boolean;
  helpText?: string;
  style?: any;
  className?: string;
  width?: any;
  renderCell?: any;
  renderHeading?: any;
  wrapCustomHeadings?: any;
}

export interface MesaDataCellProps {
  row: UserDataset;
  column: MesaColumn;
  rowIndex: number;
  columnIndex: number;
  inline?: boolean;
}