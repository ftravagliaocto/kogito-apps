import { renderHook } from '@testing-library/react-hooks';
import { act } from 'react-test-renderer';
import * as api from '../../../../utils/api/httpClient';
import useCounterfactualExecution from '../useCounterfactualExecution';
import {
  CFAnalysisResult,
  CFGoal,
  CFSearchInput,
  RemoteDataStatus
} from '../../../../types';

const flushPromises = () => new Promise(setImmediate);
const apiMock = jest.spyOn(api, 'httpClient');

beforeEach(() => {
  apiMock.mockClear();
});

describe('useCounterfactualExecution', () => {
  it('starts a new CF analysis and retrieves results', async () => {
    jest.useFakeTimers();
    const CFAnalysisResponse = {
      data: {
        executionId: '7ffd3240-2ad4-4999-b67c-9437efcd449a',
        counterfactualId: 'bb3bd63b-aaaf-46c0-9de8-88a3b405d596'
      }
    };
    const CFResultsOne = {
      data: {
        ...CFAnalysisResponse.data,
        goals: [
          {
            kind: 'UNIT',
            name: 'canRequestLoan',
            typeRef: 'boolean',
            components: null,
            value: true
          }
        ],
        searchDomains: [
          {
            fixed: false,
            kind: 'UNIT',
            name: 'monthlySalary',
            typeRef: 'number',
            components: null,
            domain: {
              type: 'RANGE',
              lowerBound: 60,
              upperBound: 6000
            }
          }
        ],
        solutions: []
      }
    };
    const CFResultsTwo = {
      data: {
        ...CFResultsOne.data,
        solutions: [
          {
            type: 'counterfactual',
            valid: true,
            executionId: '7ffd3240-2ad4-4999-b67c-9437efcd449a',
            status: 'SUCCEEDED',
            statusDetails: null,
            counterfactualId: 'bb3bd63b-aaaf-46c0-9de8-88a3b405d596',
            solutionId: 'b39779a0-fd73-42ac-8562-6976ec26273b',
            sequenceId: 3,
            isValid: true,
            stage: 'INTERMEDIATE',
            inputs: [
              {
                kind: 'UNIT',
                name: 'monthlySalary',
                typeRef: 'Double',
                components: null,
                value: 2428.5761968979696
              }
            ],
            outputs: [
              {
                kind: 'UNIT',
                name: 'canRequestLoan',
                typeRef: 'Boolean',
                value: true
              }
            ]
          }
        ] as CFAnalysisResult[]
      }
    };
    const CFResultsThree = {
      data: {
        ...CFResultsOne.data,
        solutions: [
          ...CFResultsTwo.data.solutions,
          {
            ...CFResultsTwo.data.solutions[0],
            stage: 'FINAL'
          }
        ]
      }
    };

    apiMock
      // @ts-ignore
      .mockImplementationOnce(() => Promise.resolve(CFAnalysisResponse))
      .mockImplementationOnce(() => Promise.resolve(CFResultsOne))
      .mockImplementationOnce(() => Promise.resolve(CFResultsTwo))
      .mockImplementationOnce(() => Promise.resolve(CFResultsThree));

    const { result } = renderHook(() => {
      return useCounterfactualExecution('7ffd3240-2ad4-4999-b67c-9437efcd449a');
    });

    expect(result.current.cfAnalysis).toStrictEqual({
      status: RemoteDataStatus.NOT_ASKED
    });

    act(() => {
      result.current.runCFAnalysis({
        goals: [
          {
            id: '_46B5CA54-27CA-4950-B601-63F58BC3BDFE',
            kind: 'UNIT',
            name: 'canRequestLoan',
            originalValue: false,
            role: 2,
            typeRef: 'boolean',
            value: true
          }
        ] as CFGoal[],
        searchDomains: [
          {
            kind: 'UNIT',
            name: 'monthlySalary',
            typeRef: 'number',
            components: null,
            value: 50,
            fixed: false,
            domain: {
              type: 'RANGE',
              lowerBound: 60,
              upperBound: 6000
            }
          }
        ] as CFSearchInput[]
      });
    });

    await act(async () => {
      await flushPromises();
    });

    expect(result.current.cfAnalysis).toStrictEqual({
      status: RemoteDataStatus.SUCCESS,
      data: {
        counterfactualId: 'bb3bd63b-aaaf-46c0-9de8-88a3b405d596',
        executionId: '7ffd3240-2ad4-4999-b67c-9437efcd449a'
      }
    });

    expect(result.current.cfResults).toBeUndefined();

    expect(setInterval).toHaveBeenCalledTimes(1);
    expect(setInterval).toHaveBeenLastCalledWith(expect.any(Function), 3000);

    expect(apiMock).toHaveBeenCalledTimes(1);

    await act(async () => {
      jest.advanceTimersByTime(1000 * 3);
    });

    expect(apiMock).toHaveBeenCalledTimes(2);

    expect(result.current.cfResults).toStrictEqual(CFResultsOne.data);

    await act(async () => {
      jest.advanceTimersByTime(1000 * 3);
    });

    expect(apiMock).toHaveBeenCalledTimes(3);

    expect(result.current.cfResults).toStrictEqual(CFResultsTwo.data);

    await act(async () => {
      jest.advanceTimersByTime(1000 * 3);
    });

    expect(apiMock).toHaveBeenCalledTimes(4);
    expect(result.current.cfResults).toStrictEqual(CFResultsThree.data);

    await act(async () => {
      jest.advanceTimersByTime(1000 * 3);
    });

    expect(apiMock).toHaveBeenCalledTimes(4);
    expect(result.current.cfResults).toStrictEqual(CFResultsThree.data);

    jest.useRealTimers();
  });
});
