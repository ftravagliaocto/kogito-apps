import React from 'react';
import { mount } from 'enzyme';
import {
  ItemObject,
  Outcome,
  RemoteData,
  RemoteDataStatus
} from '../../../../types';
import { MemoryRouter } from 'react-router';
import useInputData from '../../InputData/useInputData';
import useDecisionOutcomes from '../../AuditDetail/useDecisionOutcomes';
import Counterfactual from '../Counterfactual';

const executionId = 'b2b0ed8d-c1e2-46b5-3ac54ff4beae-1000';

jest.mock('../../InputData/useInputData');
jest.mock('../../AuditDetail/useDecisionOutcomes');
jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useParams: () => ({
    executionId
  })
}));

describe('Counterfactual', () => {
  test('renders loading animation while fetching data', () => {
    const loadingOutcomes = {
      status: RemoteDataStatus.LOADING
    } as RemoteData<Error, Outcome[]>;
    const loadingInputs = {
      status: RemoteDataStatus.LOADING
    } as RemoteData<Error, ItemObject[]>;

    (useDecisionOutcomes as jest.Mock).mockReturnValue(loadingOutcomes);
    (useInputData as jest.Mock).mockReturnValue(loadingInputs);

    const wrapper = mount(
      <MemoryRouter
        initialEntries={[
          {
            pathname: `/audit/decision/${executionId}/counterfactual-analysis`,
            key: 'counterfactual-analysis'
          }
        ]}
      >
        <Counterfactual />
      </MemoryRouter>
    );

    expect(useDecisionOutcomes).toHaveBeenCalledWith(executionId);
    expect(useInputData).toHaveBeenCalledWith(executionId);

    expect(wrapper.find('Title').text()).toMatch('Counterfactual Analysis');
    expect(wrapper.find('CounterfactualAnalysis')).toHaveLength(0);

    expect(
      wrapper.find('.counterfactual__wrapper SkeletonFlexStripes')
    ).toHaveLength(1);
    expect(
      wrapper.find('.counterfactual__wrapper SkeletonDataList')
    ).toHaveLength(1);
  });

  test('renders the counterfactual analysis component', () => {
    const outcomesData = {
      status: RemoteDataStatus.SUCCESS,
      data: [
        {
          outcomeId: '_12268B68-94A1-4960-B4C8-0B6071AFDE58',
          outcomeName: 'Mortgage Approval',
          evaluationStatus: 'SUCCEEDED',
          outcomeResult: {
            name: 'Mortgage Approval',
            typeRef: 'boolean',
            value: true,
            components: []
          },
          messages: [],
          hasErrors: false
        }
      ] as Outcome[]
    };
    const inputData = {
      status: RemoteDataStatus.SUCCESS,
      data: [
        {
          name: 'Asset Score',
          typeRef: 'number',
          kind: 'UNIT',
          value: 738,
          components: null
        }
      ] as ItemObject[]
    };

    (useDecisionOutcomes as jest.Mock).mockReturnValue(outcomesData);
    (useInputData as jest.Mock).mockReturnValue(inputData);

    const wrapper = mount(
      <MemoryRouter
        initialEntries={[
          {
            pathname: `/audit/decision/${executionId}/counterfactual-analysis`,
            key: 'counterfactual-analysis'
          }
        ]}
      >
        <Counterfactual />
      </MemoryRouter>
    );

    expect(
      wrapper.find('.counterfactual__wrapper SkeletonFlexStripes')
    ).toHaveLength(0);
    expect(wrapper.find('CounterfactualAnalysis')).toHaveLength(1);
    expect(
      wrapper.find('CounterfactualAnalysis').props()['inputs']
    ).toStrictEqual(inputData.data);
    expect(
      wrapper.find('CounterfactualAnalysis').props()['outcomes']
    ).toStrictEqual(outcomesData.data);
    expect(
      wrapper.find('CounterfactualAnalysis').props()['executionId']
    ).toStrictEqual(executionId);
  });
});
